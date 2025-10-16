
-----

# 🔑 Passwordless Authentication API (OTP via Email)

## 🚀 Ringkasan Proyek

Proyek ini mengimplementasikan *backend* untuk otentikasi tanpa kata sandi (*passwordless*) menggunakan **One-Time Password (OTP)** yang dikirim melalui email. Dirancang dengan prinsip **RESTful API**, proyek ini menawarkan solusi *login* yang aman, cepat, dan skalabel.

## 🌟 Fitur Utama

- **Passwordless Login:** Eliminasi risiko keamanan terkait *password* pengguna.
- **Stateless Session:** Menggunakan **JSON Web Tokens (JWT)** untuk otorisasi sesi, menjamin skalabilitas horizontal.
- **Kinerja Tinggi:** Memanfaatkan **Redis** sebagai *OTP Store* dengan **TTL** (Time-To-Live) dan **Java 25 Virtual Threads** untuk *throughput* I/O yang tinggi.
- **Keamanan Berlapis:** Dilengkapi *hashing* OTP, *rate limiting*, dan *brute-force protection*.
- **Containerized:** *Deployment* yang konsisten dan mudah menggunakan **Docker Compose**.

-----

## 🛠️ Teknologi Stack

| Komponen | Teknologi/Versi | Peran |
| :--- | :--- | :--- |
| **Backend Framework** | **Spring Boot 3.x** | Pengembangan API RESTful utama. |
| **Bahasa Pemrograman** | **Java 25** | Bahasa *backend* utama; memanfaatkan fitur *Virtual Threads* (Project Loom). |
| **Penyimpanan OTP & Caching** | **Redis Database** | Penyimpanan *hash* OTP dengan TTL (5 menit) dan *rate limiting* *counter*. |
| **Persistent DB** | **PostgreSQL** | *Database* relasional utama untuk data pengguna dan log sesi. |
| **Containerization** | **Docker Compose** | Mengelola dan menghubungkan layanan `app`, `db` (PostgreSQL), dan `redis`. |
| **Otorisasi** | **JWT** | Mekanisme otorisasi sesi *stateless* (Access & Refresh Tokens). |
| **Keamanan** | **Spring Security** | Implementasi *hashing* (bcrypt) dan validasi JWT. |

-----

## 🧱 Arsitektur Aplikasi

Aplikasi berjalan sebagai *microservice* yang diorkestrasi oleh Docker Compose.

1.  **Spring Boot Service:** Menangani logika bisnis inti. Menggunakan `RedisTemplate` untuk interaksi *cache* dan `JpaRepository` untuk interaksi PostgreSQL.
2.  **Redis:** Digunakan untuk menyimpan *hashed* OTP dengan TTL, memastikan kode kedaluwarsa secara otomatis dan cepat.
3.  **PostgreSQL:** Menyimpan *master data* pengguna dan riwayat *login* yang penting untuk audit.
4.  **Java 25 Virtual Threads:** Digunakan untuk *non-blocking* I/O pada operasi yang melibatkan layanan eksternal (*Email Service*) atau *database* (*PostgreSQL*), meningkatkan *scalability* tanpa meningkatkan penggunaan *thread* OS.

-----

## ⚙️ Instalasi & Menjalankan Proyek

### Prasyarat

- **Docker** dan **Docker Compose** terinstal.
- **Java 25 SDK** (untuk pengembangan dan *building* lokal).

### Langkah-langkah

1.  **Kloning Repositori:**

    ```bash
    git clone https://github.com/your-username/otp-api.git
    cd otp-api
    ```

2.  **Konfigurasi Lingkungan:**
    Buat file `.env` di direktori root untuk konfigurasi sensitif.

    ```properties
    # DB Configuration
    POSTGRES_DB=otp_db
    POSTGRES_USER=user
    POSTGRES_PASSWORD=password

    # Redis Configuration
    REDIS_HOST=redis
    REDIS_PORT=6379

    # Email Configuration (SendGrid)
    spring.mail.host=smtp.sendgrid.net
    spring.mail.port=587
    spring.mail.username=apikey
    spring.mail.password=your_api_key
    spring.mail.properties.mail.smtp.auth=true
    spring.mail.properties.mail.smtp.starttls.enable=true
    spring.mail.properties.mail.smtp.trust=smtp.sendgrid.net
    spring.mail.properties.mail.timeout=10000
    spring.mail.debug=false
    ```

3.  **Jalankan Kontainer dengan Docker Compose:**

    ```bash
    docker-compose up --build -d
    ```

    Perintah ini akan membangun *image* Spring Boot dan menjalankan semua kontainer (app, db, redis) di *background*.

4.  **Akses API:**
    API berjalan di `http://localhost:8080`.

-----

## 📖 RESTful API Endpoints (Alur Login)

### 1\. Meminta Kode OTP (Menciptakan Permintaan Login)

| Detail | Deskripsi |
| :--- | :--- |
| **Endpoint** | `POST /api/v1/auth/request-otp` |
| **Metode** | `POST` |
| **Request Body** | `{ "email": "user@example.com" }` |
| **Respons Sukses**| `200 OK` |
| **Best Practice** | Terapkan **Rate Limiting** (menggunakan Redis) di lapisan ini. |

### 2\. Memverifikasi Kode OTP (Memproses Otentikasi)

| Detail | Deskripsi |
| :--- | :--- |
| **Endpoint** | `POST /api/v1/auth/verify-otp` |
| **Metode** | `POST` |
| **Request Body**| `{ "email": "user@example.com", "otp": "123456" }` |
| **Respons Sukses**| `200 OK` (Mengembalikan Access Token dan Refresh Token) |
| **Respons Gagal**| `401 Unauthorized` (OTP tidak valid/kadaluarsa); `429 Too Many Requests` (Brute-Force Limit) |
| **Backend Action**| 1. Ambil *hash* OTP dari **Redis**. 2. Verifikasi dengan **timing-safe comparison**. 3. Hapus *key* OTP dari Redis. 4. *Generate* **JWT**. |

### 3\. Mengakses Sumber Daya Terproteksi

| Detail | Deskripsi |
| :--- | :--- |
| **Endpoint** | `GET /api/v1/user/profile` |
| **Metode** | `GET` |
| **Request Header**| `Authorization: Bearer <access_token>` |
| **Otorisasi** | *Spring Security* memvalidasi JWT dan mengizinkan akses. |

-----

## 🔒 Keamanan & Best Practices

| Area | Implementasi Best Practice | Tujuan Keamanan |
| :--- | :--- | :--- |
| **Penyimpanan Kode**| **OTP di-hash** menggunakan **bcrypt** dan disimpan di **Redis** dengan **TTL pendek** (5 menit). | Mencegah kebocoran *plaintext* OTP dan menjamin kode hanya sekali pakai. |
| **Brute-Force** | **Redis** digunakan sebagai *counter* kegagalan *login* per email/IP, menghasilkan respons `429`. | Mencegah serangan tebakan kode secara masif. |
| **Token Management**| **Access Token (pendek)** untuk setiap permintaan, dan **Refresh Token (panjang)** untuk penerbitan token baru. | Meminimalkan jendela waktu risiko jika token dicuri. |
| **Timing Attacks** | Spring Security memastikan perbandingan *hash* OTP adalah **timing-safe**. | Mencegah penyerang mengetahui karakter OTP yang benar berdasarkan waktu respons. |
| **TLS Wajib** | Diasumsikan deployment produksi akan selalu berada di belakang **reverse proxy** dengan **HTTPS/TLS** aktif. | Melindungi JWT dan OTP saat transit di jaringan. |