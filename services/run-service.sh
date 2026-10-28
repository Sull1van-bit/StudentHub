#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

mkdir -p "$DIR/data/notebooklm"

COMMAND="${1:-docker}"

case "$COMMAND" in
  login)
    echo "=== Menjalankan otentikasi Google NotebookLM (notebooklm login) ==="
    if [ ! -d "$DIR/.venv" ]; then
      echo "Membuat Python virtual environment di $DIR/.venv..."
      python3 -m venv "$DIR/.venv"
      "$DIR/.venv/bin/pip" install --upgrade pip
      "$DIR/.venv/bin/pip" install -r "$DIR/notebooklm-rest-api/requirements.txt"
      "$DIR/.venv/bin/playwright" install chromium
    fi
    "$DIR/.venv/bin/notebooklm" login
    if [ -f "$HOME/.notebooklm/storage_state.json" ]; then
      cp -v "$HOME/.notebooklm/storage_state.json" "$DIR/data/notebooklm/storage_state.json"
      echo "Berhasil menyalin storage_state.json ke $DIR/data/notebooklm/storage_state.json"
    fi
    ;;

  docker)
    echo "=== Menjalankan NotebookLM REST API via Docker Compose ==="
    if command -v docker >/dev/null 2>&1; then
      docker compose up --build -d
      echo "Layanan berjalan di http://localhost:8000"
      echo "Dokumentasi API Swagger: http://localhost:8000/docs"
      echo "Cek status: docker compose logs -f"
    else
      echo "Error: docker tidak ditemukan. Silakan gunakan './run-service.sh local'"
      exit 1
    fi
    ;;

  docker-stop)
    echo "=== Menghentikan NotebookLM REST API (Docker) ==="
    docker compose down
    ;;

  local)
    echo "=== Menjalankan NotebookLM REST API langsung via Python ==="
    if [ ! -d "$DIR/.venv" ]; then
      echo "Membuat virtualenv..."
      python3 -m venv "$DIR/.venv"
      "$DIR/.venv/bin/pip" install --upgrade pip
      "$DIR/.venv/bin/pip" install -r "$DIR/notebooklm-rest-api/requirements.txt"
    fi
    if [ -f "$DIR/data/notebooklm/storage_state.json" ]; then
      export NOTEBOOKLM_STORAGE_PATH="$DIR/data/notebooklm/storage_state.json"
    fi
    cd "$DIR/notebooklm-rest-api"
    "$DIR/.venv/bin/uvicorn" app:app --host 0.0.0.0 --port 8000 --reload
    ;;

  *)
    echo "Penggunaan: $0 {docker|docker-stop|login|local}"
    echo "  docker      : Jalankan container via docker compose"
    echo "  docker-stop : Hentikan container docker"
    echo "  login       : Jalankan 'notebooklm login' untuk mendapatkan storage_state.json"
    echo "  local       : Jalankan server uvicorn langsung menggunakan Python venv"
    exit 1
    ;;
esac
