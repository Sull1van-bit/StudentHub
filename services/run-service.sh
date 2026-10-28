#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

mkdir -p "$DIR/data/notebooklm"

COMMAND="${1:-docker}"

ensure_container_engine() {
  # 1. Coba periksa apakah docker daemon standar aktif
  if docker info >/dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
    return 0
  fi

  # 2. Jika di Fedora/Linux dengan Podman, gunakan podman.socket (rootless tanpa sudo)
  local uid
  uid=$(id -u)
  local podman_sock="/run/user/${uid}/podman/podman.sock"
  if command -v podman >/dev/null 2>&1; then
    systemctl --user start podman.socket >/dev/null 2>&1 || true
    if [ -S "$podman_sock" ]; then
      export DOCKER_HOST="unix://${podman_sock}"
      if docker compose version >/dev/null 2>&1; then
        COMPOSE_CMD="docker compose"
        return 0
      elif podman compose version >/dev/null 2>&1; then
        COMPOSE_CMD="podman compose"
        return 0
      fi
    fi
  fi

  echo ""
  echo "⚠️  Docker daemon belum aktif (docker.sock tidak ditemukan)."
  echo "Pilih salah satu solusi praktis berikut:"
  echo ""
  echo "  A) Aktifkan Docker service (butuh sudo):"
  echo "     sudo systemctl start docker"
  echo ""
  echo "  B) Aktifkan Podman socket (tanpa sudo di Fedora/Linux):"
  echo "     systemctl --user enable --now podman.socket"
  echo "     export DOCKER_HOST=\"unix:///run/user/\$(id -u)/podman/podman.sock\""
  echo ""
  echo "  C) Jalankan langsung menggunakan Python lokal (paling cepat & ringan):"
  echo "     ./services/run-service.sh local"
  echo ""
  exit 1
}

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
    echo "=== Menjalankan NotebookLM REST API via Container ==="
    ensure_container_engine
    $COMPOSE_CMD up --build -d
    echo "Layanan berjalan di http://localhost:8000"
    echo "Dokumentasi API Swagger: http://localhost:8000/docs"
    echo "Cek status: $COMPOSE_CMD logs -f"
    ;;

  docker-stop)
    echo "=== Menghentikan NotebookLM REST API (Container) ==="
    ensure_container_engine
    $COMPOSE_CMD down
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
