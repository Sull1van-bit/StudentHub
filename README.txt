================================================================================
                    STUDENTHUB - UMN IF570L MIDTERM PROJECT
================================================================================

Nama Aplikasi    : StudentHub (All-in-One Student Dashboard)
Mata Kuliah      : Pemrograman Aplikasi Mobile (IF570L)
Institusi        : Universitas Multimedia Nusantara (UMN)
Tahun Akademik   : Semester Ganjil 2026/2027

--------------------------------------------------------------------------------
1. DESKRIPSI APLIKASI & SOLUSI
--------------------------------------------------------------------------------
StudentHub adalah aplikasi produktivitas perkuliahan terpadu berbasis Android lokal
(offline-first) yang dirancang khusus untuk memenuhi kebutuhan akademik dan
keseharian mahasiswa.

Aplikasi ini mengatasi fragmentasi pencatatan perkuliahan yang selama ini terpisah
antara aplikasi kalender, kalkulator IPK manual, pencatat keuangan/uang saku,
serta aplikasi catatan materi perkuliahan.

Fitur Utama:
1. Onboarding / Start Screen:
   - Wizard interaktif 5 langkah untuk inisialisasi data pengguna baru:
     a. Identitas Mahasiswa (Nama & Program Studi).
     b. Pemilihan Semester Aktif (default Semester 5).
     c. Riwayat Akademik: Pertanyaan riwayat IPK kumulatif sebelumnya (default 3.15
        sesuai portal akademik UMN).
     d. Anggaran & Pemasukan Bulanan: Input uang saku bulanan yang otomatis tercatat
        sebagai transaksi pemasukan awal di database SQLite.
     e. Konfirmasi Mata Kuliah & Jadwal Semester 5 (Total 19 SKS sesuai portal):
        * EM105 - Technopreneurship (3 SKS)
        * UM321 - English 3 (2 SKS)
        * IF570 - Mobile Application Programming (3 SKS)
        * IF590 - Information Technology Research (2 SKS)
        * IF542 - Deep Learning (3 SKS)
        * IF571 - Cybersecurity (3 SKS)
        * IF581 - Game Development (3 SKS)
   - Catatan awal dikosongkan (100% clean slate) agar pengguna dapat menulis catatan
     pribadi secara riil.
   - Opsi "Ulangi Onboarding" dapat diakses kapan saja melalui Profil atau Drawer Menu.

2. Dashboard Utama (Today Screen):
   - Sapaan dinamis sesuai nama pengguna dari database ("Selamat pagi, [Nama].").
   - Tanggal riil dinamis mengikuti sistem (`LocalDate.now()`, misal: "Rabu, 14 Oktober 2026").
   - Live Ticking Clock (jam berjalan realtime berbasis `LocalTime.now()`).
   - Kartu Alert Pengingat Kelas Interaktif: Membaca jadwal nyata dari database SQLite
     (`db.agenda`) dan menghitung selisih waktu dinamis hingga kelas berikutnya dimulai
     (misal: "Akan ada kelas IF570 dalam 45 menit lagi (09.00)") atau menampilkan status kelas
     yang sedang berlangsung.
   - Kartu "Kelas Berikutnya" dinamis dengan rincian jadwal, waktu, dan ruang kuliah dari database.
   - Ringkasan sisa agenda perkuliahan hari ini yang sinkron langsung dengan SQLite.
   - Section pengingat statis di Homescreen telah dihilangkan agar antarmuka lebih bersih dan fokus.
   - Pintasan Ruang Mahasiswa (Catatan Kuliah, Keuangan, Nilai, Belajar).

3. Kalender & Jadwal Kuliah (Schedule):
   - Strip Kalender Mingguan Riil: Tanggal dinamis dihitung dari `LocalDate.now()` dari
     Senin hingga Minggu dengan nomor tanggal kalender nyata (misal: 12, 13, 14, 15, 16, 17, 18).
   - Penanda Visual "Hari Ini" / "Today" dengan badge khusus dan border aksen pada kolom hari ini.
   - Indikator titik jadwal (dot indicator) pada tanggal yang memiliki agenda kelas.
   - Tombol pintas "Ke Hari Ini" / "Today" untuk kembali ke jadwal hari aktif dengan satu sentuhan.
   - Tampilan Empty State yang ramah saat tidak ada kelas di hari yang dipilih.
   - Fitur "Tambah Agenda": Input judul dan jam (format HH.mm) dengan validasi format.
   - Fitur inspeksi dan hapus agenda dari SQLite.

4. Pelacak Akademik & Kalkulator IPK (Academic & Grades):
   - Perhitungan dinamis IPS (Indeks Prestasi Semester) dan IPK Kumulatif:
     IPK = ((Prior IPK × Prior SKS) + Nilai Berjalan SKS) / Total SKS.
   - Visualisasi grafik perjalanan IPS multi-semester.
   - Dialog "Edit Nilai": Input skor (0-100) dengan keyboard angka, auto-assign
     nilai huruf (A, A-, B+, B, B-, C+, C, D, E), feedback validasi, dan update
     instan ke database SQLite.
   - Target IPK belajar mandiri dengan checklist tugas materi.
   - Halaman Detail Mata Kuliah dengan informasi dosen, ruangan, progres tugas,
     serta tombol langsung untuk membuka catatan dan memindai dokumen fisik.

5. Pencatatan Keuangan Mahasiswa (Finance):
   - Ringkasan saldo kas lokal, total pemasukan, dan total pengeluaran.
   - Filter transaksi berdasarkan tipe ("Semua", "Pengeluaran", "Pemasukan")
     dan filter berdasarkan kategori pengeluaran ("Makan", "Transportasi", "Kuliah", "Kos", "Lainnya").
   - Progress bar penggunaan anggaran bulanan per kategori.
   - Dialog "Catat Transaksi" dengan validasi nominal angka bulat, tanggal, dan kategori.
   - Dialog inspeksi rincian transaksi dan tombol hapus transaksi lokal dari SQLite.

6. Notetaking Perkuliahan (Notes & Editor):
   - Modul Catatan Kuliah yang bersih untuk mahasiswa membuat catatan pribadi.
   - Pengelompokan catatan berdasarkan mata kuliah semester 5.
   - Pencarian catatan berdasarkan kata kunci judul dan isi teks.
   - Rich Text Editor catatan dengan formatting cepat (Tebal, Miring, Bullet List).
   - Kartu banner dan tombol "Pindai Catatan" terintegrasi langsung di modul Catatan.

7. Fitur Native Hardware Mockup (Camera Viewfinder & OCR Mockup):
   - Terletak eksklusif pada modul Catatan (Notes Screen, Detail Matkul, dan Drawer).
   - Layar pemindai kamera interaktif:
     * Viewfinder kamera dengan framing dokumen dan animasi garis laser scanning.
     * Toggle Flash (Nyala/Mati).
     * Toggle Kamera Depan / Belakang.
     * Tombol rana (shutter) untuk menangkap foto dokumen catatan.
     * Tiga mode filter dokumen: B&W Dokumen, Kontras Tinggi, dan Original.
     * Simulasi ekstraksi teks otomatis (OCR Text Extraction Preview) sesuai mata kuliah.
     * Tombol "Simpan ke Catatan": Menyimpan hasil scan dokumen fisik langsung
       ke database catatan perkuliahan.

8. Dukungan Multi-Bahasa Menyeluruh (100% Full Bilingual Support - ID & EN):
   - Pilihan bahasa langsung di menu Profil: "🇮🇩 Indonesia" dan "🇬🇧 English".
   - Tersimpan secara persisten di SharedPreferences (`studenthub_prefs`).
   - Cakupan lokalisasi 100% di semua layar: Onboarding Wizard, Homescreen, Kalender/Jadwal,
     Akademik & Nilai, Detail Matkul, Keuangan, Catatan & Editor, Pemindai Dokumen,
     Pengaturan Tema, TopAppBar, BottomNav, serta Nav Drawer.
   - Bahasa Indonesia menggunakan gaya bahasa natural aplikasi modern (ramah, tidak kaku).
   - Menjaga integritas exact string assertion pada pengujian otomatis Robolectric.

--------------------------------------------------------------------------------
2. PENYIMPANAN DATA LOKAL & ARSITEKTUR
--------------------------------------------------------------------------------
Aplikasi StudentHub 100% berjalan offline tanpa backend server eksternal, sesuai
persyaratan UTS IF570L:

A. Database SQLite (StudentHubDatabase.kt):
   - Tabel `profile`: Menyimpan nama mahasiswa, program studi, dan semester berjalan.
   - Tabel `courses`: Menyimpan kode matkul, nama matkul, SKS, semester, skor, dan nilai huruf.
   - Tabel `agenda`: Menyimpan jadwal kuliah dan kegiatan mingguan (day 0-6, time, title).
   - Tabel `transactions`: Menyimpan pencatatan keuangan (title, amount, category, income flag, date).
   - Tabel `budgets`: Menyimpan pagu anggaran bulanan per kategori.
   - Tabel `academic_goal`: Menyimpan target IPK pengguna.
   - Sanitasi data: Transaksi atomik (beginTransaction, setTransactionSuccessful, endTransaction),
     validasi batasan nilai (CHECK constraint pada SQLite).

B. SharedPreferences:
   - `studenthub_settings`: Menyimpan preferensi tema UI (Sistem, Terang, Gelap).
   - `studenthub_prefs`: Menyimpan status onboarding dan preferensi bahasa (id / en).
   - `studenthub_notes`: Menyimpan konten catatan perkuliahan lokal.

C. Navigasi & UI:
   - Framework: Jetpack Compose + Material 3 Design.
   - Navigation: androidx.navigation.compose:2.8.8 (NavHost & NavController).
   - Routing: home, onboarding, schedule, academic, course/{courseId}, notes,
     editor?noteId={noteId}&courseId={courseId}, scan_note?courseId={courseId},
     finance, grades, learning, profile.

--------------------------------------------------------------------------------
3. PEMENUHAN RUBRIK PENILAIAN UTS
--------------------------------------------------------------------------------
[x] 1. Multiple Screen & Navigation:
       Menggunakan Jetpack Compose Navigation (NavHost, NavController) dengan 10+ rute
       layar, passing arguments dinamis, serta navigasi BottomBar dan Drawer.
[x] 2. Form Input & Validasi Komprehensif:
       Seluruh form (Onboarding, Tambah Agenda, Catat Transaksi, Edit Nilai, Editor Catatan)
       menerapkan validasi ketat: isError highlight, supportingText keterangan error,
       KeyboardType yang sesuai (Number/Text), serta pencegahan input kosong atau di luar batas.
[x] 3. Database SQLite:
       Penyimpanan lokal menggunakan SQLiteOpenHelper dengan query terstruktur (INSERT,
       UPDATE, DELETE, SELECT), transaksional ACID, serta data seeding yang konsisten.
[x] 4. Native Feature (Mockup UI):
       Implementasi antarmuka pemindai catatan kamera (ScanNoteScreen) lengkap dengan
       viewfinder overlay, animasi scan laser, toggle flash/flip, filter gambar, OCR preview,
       dan integrasi simpan ke modul Catatan.
[x] 5. UI/UX, Bilingual & Theming:
       Mengikuti panduan Material 3 Design, mendukung Tema Terang & Tema Gelap, kontras
       warna ramah aksesibilitas, dukungan dwi-bahasa (Indonesia & Inggris), dan layout responsif.
[x] 6. Automated Testing:
       100% lulus seluruh unit test & Robolectric test (39/39 tests passed).

--------------------------------------------------------------------------------
4. ANGGOTA KELOMPOK & PEMBAGIAN TUGAS
--------------------------------------------------------------------------------
Kelompok      : StudentHub Team
Anggota 1     : Rafael Romelo Gibran (000000xxxxx)
                - Peran: Lead Android Developer & Architecture
                - Kontribusi:
                  * Perancangan arsitektur Jetpack Compose Navigation & SQLite Database.
                  * Implementasi Onboarding Wizard (Start Screen) 5 langkah.
                  * Integrasi dinamis IPK Kumulatif, modul Finance, dan modul Agenda.
                  * Implementasi fitur Mockup Kamera Pemindai Catatan & OCR (ScanNoteScreen).
                  * Penulisan automated test suite (Robolectric & Compose UI Tests).

Anggota 2     : [Nama Anggota 2] (000000xxxxx)
                - Peran: UI/UX & Module Developer (Academic & Schedule)
                - Kontribusi:
                  * Desain antarmuka Material 3 (Shape, Color, Typography).
                  * Implementasi ScheduleScreen & CourseDetailScreen.
                  * Form validasi edit nilai dan input agenda.
                  * Pengujian fungsionalitas kalender dan sinkronisasi data SQLite.

Anggota 3     : [Nama Anggota 3] (000000xxxxx)
                - Peran: Module Developer (Finance, Notes, Testing & QA)
                - Kontribusi:
                  * Implementasi modul Finance (filter transaksi, kategori anggaran).
                  * Implementasi Notetaking editor dan penyimpanan catatan.
                  * Pembuatan dokumentasi proyek (README.txt) dan skenario video demo.
                  * Quality assurance pengujian tema gelap/terang dan build APK.

--------------------------------------------------------------------------------
5. SKENARIO DEMO APLIKASI (PANDUAN VIDEO DEMO)
--------------------------------------------------------------------------------
Durasi Rekomendasi: 3 - 5 Menit

1. Scene 1 - Intro & Start Screen (Onboarding):
   - Buka aplikasi untuk pertama kali (layar Onboarding muncul).
   - Tunjukkan Step 1: Masukkan Nama & Program Studi.
   - Tunjukkan Step 2: Masukkan Semester ("5").
   - Tunjukkan Step 3: Muncul pertanyaan riwayat IPK semester lalu (karena semester > 1).
     Aktifkan opsi dan masukkan IPK lalu ("3.15").
   - Tunjukkan Step 4: Masukkan uang saku bulanan ("Rp2.000.000").
   - Tunjukkan Step 5: Tinjau 7 mata kuliah semester 5 (EM105, UM321, IF570, IF590, IF542, IF571, IF581).
   - Klik "Mulai Gunakan StudentHub".

2. Scene 2 - Dashboard (Today Screen):
   - Tunjukkan sapaan personal "Selamat pagi, [Nama]."
   - Tinjau kartu kelas berikutnya dan sisa agenda hari ini.
   - Buka Pengingat melalui ikon lonceng di TopBar.

3. Scene 3 - Kalender & Jadwal Kuliah (Schedule):
   - Navigasi ke menu "Jadwal" melalui bottom bar.
   - Geser navigasi hari dari Senin sampai Minggu.
   - Klik tombol "Tambah agenda" -> tunjukkan validasi format jam ->
     masukkan waktu "19.30" dan judul "Belajar Mandiri Jetpack Compose" -> klik Simpan.
   - Tunjukkan agenda baru berhasil muncul di daftar jadwal.

4. Scene 4 - Pelacak Akademik & IPK (Academic & Grades):
   - Buka menu "Akademik" dari Drawer atau ikon TopBar.
   - Tinjau daftar mata kuliah dan klik salah satu (IF570).
   - Masuk ke tab "Nilai" / "Pelacak Nilai" di menu.
   - Tunjukkan nilai IPS dan IPK Kumulatif yang dihitung dinamis dari data SQLite.
   - Klik "Edit nilai" -> coba masukkan skor di luar batas (misal 150) -> tunjukkan
     pesan error validasi -> ubah skor ke 95 -> nilai huruf otomatis terbarui ke "A" ->
     simpan -> tunjukkan perubahan skor dan recalculate IPK kumulatif.

5. Scene 5 - Pencatatan Keuangan (Finance):
   - Buka menu "Keuangan".
   - Tunjukkan saldo awal dari uang saku onboarding.
   - Uji filter "Pengeluaran", "Pemasukan", dan filter per kategori.
   - Klik "Catat transaksi" -> masukkan nominal 25000, judul "Makan Siang Soto", kategori Makan -> Simpan.
   - Tunjukkan transaksi tercatat dan progres anggaran bertambah.

6. Scene 6 - Notetaking & Fitur Native Mockup (Scan Note):
   - Buka menu "Catatan" (tunjukkan catatan kosong siap dipakai oleh pengguna baru).
   - Klik tombol "Pindai Catatan" (fitur mockup kamera OCR).
   - Tunjukkan viewfinder kamera, animasi laser scan, toggle flash, toggle kamera depan/belakang.
   - Tekan tombol capture (shutter).
   - Tunjukkan preview hasil foto dokumen, pilih filter (B&W Dokumen), dan teks OCR yang diekstraksi.
   - Klik "Simpan ke Catatan" -> kembali ke layar catatan -> tunjukkan catatan hasil scan tersimpan.

7. Scene 7 - Pengaturan Bahasa, Profil, Tema, & Penutup:
   - Buka menu "Profil & tampilan".
   - Tunjukkan Language Switcher -> ganti ke "🇬🇧 English" -> seluruh antarmuka berubah ke bahasa Inggris seketika.
   - Ganti kembali ke "🇮🇩 Indonesia".
   - Ubah tema ke "Gelap" (Dark Mode) -> tunjukkan perubahan kontras warna UI.
   - Tunjukkan tombol "Mulai Ulang Setup Pengguna (Onboarding)" jika ingin re-run wizard.
   - Penutup presentasi video.

================================================================================
HASIL BUILD APK:
File APK debug siap diuji dan dikumpulkan:
app/build/outputs/apk/debug/app-debug.apk
================================================================================
