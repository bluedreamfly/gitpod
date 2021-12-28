// Copyright (c) 2021 Gitpod GmbH. All rights reserved.
// Licensed under the GNU Affero General Public License (AGPL).
// See License-AGPL.txt in the project root for license information.

package sshproxy

import (
	"fmt"
	"net"
	"time"

	"golang.org/x/crypto/ssh"
)

type Remote struct {
	Address string

	AuthKey ssh.Signer
}

type Session struct {
	Conn *ssh.ServerConn

	WorkspaceId string

	Remote *Remote

	PublicKey ssh.PublicKey
}

type Server struct {
	Setup func(*Session) error

	ConnectionTimeout time.Duration

	sshConfig *ssh.ServerConfig
}

// HandleConn takes a net.Conn and runs it through sshmux.
func (s *Server) HandleConn(c net.Conn) {
	sshConn, chans, reqs, err := ssh.NewServerConn(c, s.sshConfig)
	if err != nil {
		c.Close()
		return
	}
	defer sshConn.Close()

	workspaceId, err := s.Authenticator(sshConn)
	if err != nil {
		return
	}

	session := &Session{
		Conn:        sshConn,
		WorkspaceId: workspaceId,
	}

	s.Setup(session)

	go func() {
		for req := range reqs {
			switch req.Type {
			case "keepalive@openssh.com":
				if req.WantReply {
					req.Reply(true, []byte{})
				}
			default:
				req.Reply(false, []byte{})
			}
		}
	}()

	for newChannel := range chans {
		switch newChannel.ChannelType() {
		case "session":
			go s.SessionForward(session, newChannel)
		case "direct-tcpip":
			go s.ChannelForward(session, newChannel)
		case "tcpip-forward":
			newChannel.Reject(ssh.UnknownChannelType, "Gitpod SSH Gateway cannot remote forward ports")
		default:
			newChannel.Reject(ssh.UnknownChannelType, fmt.Sprintf("Gitpod SSH Gateway cannot handle %s channel types", newChannel.ChannelType()))
		}
	}
}

func (s *Server) Authenticator(ssh.ConnMetadata) (workspaceId string, err error) {
	return "", nil
}

func (s *Server) Serve(l net.Listener) error {
	for {
		conn, err := l.Accept()
		if err != nil {
			return err
		}

		go s.HandleConn(conn)
	}
}

func New(signer ssh.Signer, setup func(*Session) error) *Server {
	server := &Server{
		Setup: setup,
	}

	server.sshConfig = &ssh.ServerConfig{
		NoClientAuth:  true,
		ServerVersion: "SSH-2.0-GITPOD-GATEWAY",
	}
	server.sshConfig.AddHostKey(signer)

	return server
}
