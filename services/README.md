# 🚀 StudentHub NotebookLM REST API Microservice

Layanan ini adalah REST API mandiri (microservice) berbasis [gnh1201/notebooklm-rest-api](https://github.com/gnh1201/notebooklm-rest-api) yang menghubungkan aplikasi StudentHub dengan Google NotebookLM secara asinkron tanpa digabung ke dalam monolith aplikasi mobile.

---

## 🏗️ Arsitektur

```
Android App (StudentHub)
    │
    ▼ (HTTP REST)
NotebookLM REST API (FastAPI / Docker Port 8000)
    │
    ▼ (notebooklm-py)
Google NotebookLM Web Services
```

- **Aplikasi Android tetap ringan**: Tidak dibebani oleh dependencies Python/Playwright.
- **Microservice Terpisah**: Dijalankan secara terisolasi via Docker atau Python virtualenv di server / komputer lokal.
- **Hybrid & Graceful Fallback**: Jika service ini aktif, StudentHub akan memanfaatkan Q&A dan artefak resmi NotebookLM. Jika service sedang tidak aktif, StudentHub secara otomatis kembali ke Gemini AI langsung.

---

## 🛠️ Persiapan & Otentikasi (Satu Kali)

Google NotebookLM memerlukan sesi login Google. Sesi ini disimpan dalam berkas `storage_state.json`.

1. Jalankan proses login satu kali:
   ```bash
   ./services/run-service.sh login
   ```
   Browser Chromium akan terbuka untuk login akun Google. Setelah selesai, berkas `storage_state.json` akan otomatis tersimpan di `services/data/notebooklm/storage_state.json`.

---

## 🐳 Menjalankan Layanan

### Opsi A: Menggunakan Docker Compose (Direkomendasikan)

Pastikan Docker sudah terpasang dan berjalan:

```bash
cd services
docker compose up --build -d
```
Atau menggunakan helper script dari root project:
```bash
./services/run-service.sh docker
```

- Endpoint API: `http://localhost:8000`
- Swagger UI / Docs: `http://localhost:8000/docs`
- Health check: `http://localhost:8000/health`

Untuk menghentikan:
```bash
./services/run-service.sh docker-stop
```

> [!TIP]
> **Jika muncul error `failed to connect to the docker API at unix:///var/run/docker.sock`:**
> 1. **Docker service belum aktif**: Jalankan `sudo systemctl start docker`.
> 2. **Jika menggunakan Fedora / Podman**: Jalankan `systemctl --user enable --now podman.socket` lalu `export DOCKER_HOST="unix:///run/user/$(id -u)/podman/podman.sock"`, atau cukup gunakan script `./services/run-service.sh docker` (script ini otomatis mendeteksi Podman).
> 3. **Atau tanpa Docker sama sekali**: Gunakan Opsi B (Python langsung) di bawah ini.

### Opsi B: Menjalankan Langsung via Python (Tanpa Docker)

```bash
./services/run-service.sh local
```

---

## 📱 Menghubungkan ke Aplikasi StudentHub Android

Secara default, aplikasi Android StudentHub dikonfigurasi untuk memeriksa layanan di:
- **Android Emulator**: `http://10.0.2.2:8000` (IP host dari emulator)
- **HP Fisik (LAN / Wi-Fi)**: Masukkan alamat IP komputer lokalmu, misalnya `http://192.168.1.50:8000` pada pengaturan atau `local.properties`.

### Menetapkan URL di `local.properties`:
```properties
NOTEBOOKLM_BASE_URL=http://10.0.2.2:8000
# Opsional: Jika menggunakan proteksi API key di microservice
NOTEBOOKLM_API_KEY=
```

Aplikasi StudentHub juga memiliki tombol pengaturan di **Studio Belajar** untuk mengganti atau menguji URL layanan secara langsung dari layar HP tanpa perlu kompilasi ulang.
