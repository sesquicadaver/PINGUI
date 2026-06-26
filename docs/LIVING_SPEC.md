# Living Specification — PINGUI

Матриця відповідності «ТЗ → модуль → тести». Оновлюється при кожній фічі.

| ТЗ (джерело) | Вимога | Модуль | Тести | Статус |
|--------------|--------|--------|-------|--------|
| 3.txt | In-memory сесійне сховище | `monitor/session_store.py` | `tests/unit/test_session_store.py` | done |
| 3.txt | Traceroute ICMP TTL 1..N | `icmp/tracer.py` | `tests/contract/test_tracer.py` | done |
| 3.txt | Raw ICMP probe | `icmp/raw_socket.py` | `tests/contract/test_tracer.py` | done |
| 3.txt | Polling + worker cycle | `monitor/polling.py`, `monitor/worker.py` | `tests/unit/test_polling.py` | done |
| 3.txt | PyQt6 GUI + топограф | `ui/main_window.py`, `ui/graph_canvas.py` | `tests/integration/test_ui_smoke.py` | done |
| 3.txt | До 10 хостів з конфігу | `config.py` | `tests/unit/test_config.py` | done |
| 2.txt | Linux cap_net_raw | `icmp/raw_socket.py`, `scripts/check_caps.sh` | manual | done |
| 3.txt | CLI параметри | `__main__.py` | manual | done |
