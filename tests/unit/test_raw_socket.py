"""Raw socket helper tests."""

from __future__ import annotations

from unittest.mock import MagicMock, patch

import pytest

from pingui.icmp.raw_socket import (
    RawIcmpPermissionError,
    ScapyProbeTransport,
    check_raw_icmp_permission,
)


def test_check_permission_ok() -> None:
    mock_sock = MagicMock()
    with patch("pingui.icmp.raw_socket.socket.socket", return_value=mock_sock):
        check_raw_icmp_permission()
    mock_sock.close.assert_called_once()


def test_check_permission_denied() -> None:
    with patch(
        "pingui.icmp.raw_socket.socket.socket",
        side_effect=PermissionError("denied"),
    ), pytest.raises(RawIcmpPermissionError, match="cap_net_raw"):
        check_raw_icmp_permission()


def test_scapy_transport_timeout() -> None:
    transport = ScapyProbeTransport()
    with patch("pingui.icmp.raw_socket.sr1", return_value=None):
        assert transport.send_probe("8.8.8.8", 1, 0.1) is None
