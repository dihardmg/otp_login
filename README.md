
-----

# 🔑 Passwordless Authentication API (OTP via Email)

## 🚀 Ringkasan Proyek

Proyek ini mengimplementasikan *backend* untuk otentikasi tanpa kata sandi (*passwordless*) menggunakan **One-Time Password (OTP)** yang dikirim melalui email. Dirancang dengan prinsip **RESTful API**, proyek ini menawarkan solusi *login* yang aman, cepat, dan skalabel.

## 🌟 Fitur Utama

- **Passwordless Login:** Eliminasi risiko keamanan terkait *password* pengguna.
- **Stateless Session:** Menggunakan **JSON Web Tokens (JWT)** untuk otorisasi sesi, menjamin skalabilitas horizontal.
- **Kinerja Tinggi & Resiliensi:** Memanfaatkan **Redis** sebagai *OTP Store* dengan **TTL** (Time-To-Live) dan **fallback in-memory** untuk kehandalan tinggi.
- **Java 25 Virtual Threads:** Menggunakan **Virtual Threads** untuk *throughput* I/O yang tinggi dan skalabilitas non-blocking.
- **Keamanan Berlapis:** Dilengkapi *hashing* OTP, *rate limiting* (5 permintaan per email), *brute-force protection*, dan validasi email terdaftar.
- **Containerized:** *Deployment* yang konsisten dan mudah menggunakan **Docker Compose**.
- **Email Validation:** Validasi email terdaftar di database sebelum mengirim OTP.
- **Refresh Token Management:** Sesi persisten dengan refresh token berdurasi 30 hari.

-----

## 🛠️ Teknologi Stack

| Komponen | Teknologi/Versi | Peran |
| :--- | :--- | :--- |
| **Backend Framework** | **Spring Boot 3.x** | Pengembangan API RESTful utama. |
| **Bahasa Pemrograman** | **Java 25** | Bahasa *backend* utama; memanfaatkan fitur *Virtual Threads* (Project Loom). |
| **Penyimpanan OTP & Caching** | **Redis Database + In-Memory Fallback** | Penyimpanan *hash* OTP dengan TTL (5 menit) dan *rate limiting*; fallback in-memory untuk resiliensi. |
| **Persistent DB** | **PostgreSQL** | *Database* relasional utama untuk data pengguna dan log sesi. |
| **Email Service** | **SendGrid** | Pengiriman OTP melalui email dengan HTML template support. |
| **Rate Limiting** | **Bucket4j** | Implementasi rate limiting per email dan IP address. |
| **Containerization** | **Docker Compose** | Mengelola dan menghubungkan layanan `app`, `db` (PostgreSQL), dan `redis`. |
| **Otorisasi** | **JWT** | Mekanisme otorisasi sesi *stateless* (Access: 15m, Refresh: 30d). |
| **Keamanan** | **Spring Security** | Implementasi *hashing* (bcrypt) dan validasi JWT. |

-----

## 🧱 Arsitektur Aplikasi

Aplikasi berjalan sebagai *microservice* yang diorkestrasi oleh Docker Compose dengan arsitektur yang resilien.

1.  **Spring Boot Service:** Menangani logika bisnis inti. Menggunakan `RedisTemplate` untuk interaksi *cache* dan `JpaRepository` untuk interaksi PostgreSQL.
2.  **Redis dengan Fallback:** Digunakan untuk menyimpan *hashed* OTP dengan TTL, memastikan kode kedaluwarsa secara otomatis. Dilengkapi fallback in-memory untuk kehandalan tinggi saat Redis tidak tersedia.
3.  **PostgreSQL:** Menyimpan *master data* pengguna dan riwayat *login* yang penting untuk audit, termasuk tracking IP address dan failed attempts.
4.  **Java 25 Virtual Threads:** Digunakan untuk *non-blocking* I/O pada operasi yang melibatkan layanan eksternal (*Email Service*) atau *database* (*PostgreSQL*), meningkatkan *scalability* tanpa meningkatkan penggunaan *thread* OS.
5.  **Rate Limiting Layer:** Implementasi multi-level rate limiting (IP-based dan email-based) menggunakan Bucket4j untuk mencegah abuse.

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
- cek log terminal
    ```
    docker logs -f otp_login-app-1
    ```

    Perintah ini akan membangun *image* Spring Boot dan menjalankan semua kontainer (app, db, redis) di *background*.

4.  **Akses API:**
    API berjalan di `http://localhost:8080`.

### Development Mode (Tanpa Redis)

Untuk development tanpa Redis:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.redis.enabled=false"
```

Aplikasi akan secara otomatis menggunakan in-memory fallback untuk penyimpanan OTP.

### Docker Services Only (PostgreSQL + Redis)

```bash
docker-compose up postgres redis -d
./mvnw spring-boot:run
```

### Environment Configuration

Konfigurasi penting di `application.properties`:

```properties
# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Email Configuration (SendGrid)
spring.mail.password=your_sendgrid_api_key

# JWT Configuration
app.jwt.secret=your_jwt_secret_key
app.jwt.access-token-expiration-minutes=15
app.jwt.refresh-token-expiration-days=30

# OTP Configuration
app.otp.expiration-minutes=5
app.otp.length=6
app.otp.max-attempts=3

# Rate Limiting
app.rate-limit.requests-per-minute=10
app.rate-limit.max-otp-attempts=5

# Fallback Configuration
app.redis.enabled=true  # Set false untuk development
```

-----

## 📖 RESTful API Endpoints (Alur Login)

### 1\. Meminta Kode OTP (Menciptakan Permintaan Login)

| Detail | Deskripsi |
| :--- | :--- |
| **Endpoint** | `POST /api/v1/auth/request-otp` |
| **Metode** | `POST` |
| **Request Body** | `{ "email": "user@example.com" }` |
| **Respons Sukses**| `200 OK` |
| **Respons Gagal**| `400 Bad Request` (Email tidak valid/tidak terdaftar); `429 Too Many Requests` (Rate limit terlampaui) |
| **Best Practice** | Rate limiting 5 permintaan per email per menit. |

### 2\. Memverifikasi Kode OTP (Memproses Otentikasi)

| Detail | Deskripsi |
| :--- | :--- |
| **Endpoint** | `POST /api/v1/auth/verify-otp` |
| **Metode** | `POST` |
| **Request Body**| `{ "email": "user@example.com", "otp": "123456" }` |
| **Respons Sukses**| `200 OK` (Mengembalikan Access Token dan Refresh Token) |
| **Respons Gagal**| `401 Unauthorized` (OTP tidak valid/kadaluarsa); `429 Too Many Requests` (Brute-Force Limit) |
| **Backend Action**| 1. Ambil *hash* OTP dari **Redis** atau **in-memory fallback**. 2. Verifikasi dengan **timing-safe comparison**. 3. Hapus *key* OTP. 4. *Generate* **JWT**. 5. Log login attempt ke database. |

### 3\. Refresh Token (Perpanjang Sesi)

| Detail | Deskripsi |
| :--- | :--- |
| **Endpoint** | `POST /api/v1/auth/refresh-token` |
| **Metode** | `POST` |
| **Request Body**| `{ "refreshToken": "refresh_token_here" }` |
| **Respons Sukses**| `200 OK` (Access Token baru) |
| **Respons Gagal**| `401 Unauthorized` (Refresh token tidak valid); `400 Bad Request` (Refresh token hilang) |

### 4\. Mengakses Sumber Daya Terproteksi

| Detail | Deskripsi |
| :--- | :--- |
| **Endpoint** | `GET /api/v1/user/profile` |
| **Metode** | `GET` |
| **Request Header**| `Authorization: Bearer <access_token>` |
| **Otorisasi** | *Spring Security* memvalidasi JWT dan mengizinkan akses. |

### 5\. Endpoint Terproteksi Lainnya

| Endpoint | Metode | Deskripsi |
| :--- | :--- | :--- |
| `GET /api/v1/user/stats` | GET | Statistik login user |
| `PUT /api/v1/user/profile` | PUT | Update profile user |
| `POST /api/v1/user/deactivate` | POST | Deactivate account |
| `POST /api/v1/auth/logout` | POST | Logout user |

-----

## 🔒 Keamanan & Best Practices

| Area | Implementasi Best Practice | Tujuan Keamanan |
| :--- | :--- | :--- |
| **Penyimpanan Kode**| **OTP di-hash** menggunakan **bcrypt** dan disimpan di **Redis** atau **in-memory fallback** dengan **TTL pendek** (5 menit). | Mencegah kebocoran *plaintext* OTP dan menjamin kode hanya sekali pakai. |
| **Brute-Force Protection** | **Rate limiting** 5 permintaan per email per menit, 3 maksimal percobaan OTP, dan tracking per IP address. | Mencegah serangan tebakan kode secara masif. |
| **Email Validation** | Validasi email terdaftar di database sebelum mengirim OTP. | Mencegah enumerasi email dan penyalahgunaan. |
| **Token Management**| **Access Token (15 menit)** untuk setiap permintaan, dan **Refresh Token (30 hari)** untuk penerbitan token baru. | Meminimalkan jendela waktu risiko jika token dicuri. |
| **Timing Attacks** | Spring Security memastikan perbandingan *hash* OTP adalah **timing-safe**. | Mencegah penyerang mengetahui karakter OTP yang benar berdasarkan waktu respons. |
| **Audit & Logging** | Semua percobaan login dicatat dengan IP address, timestamp, dan status keberhasilan. | Tracking keamanan dan forensic analysis. |
| **Fallback Resiliency** | Otomatis switch ke in-memory storage jika Redis tidak tersedia. | Ketersediaan layanan tinggi (high availability). |
| **TLS Wajib** | Diasumsikan deployment produksi akan selalu berada di belakang **reverse proxy** dengan **HTTPS/TLS** aktif. | Melindungi JWT dan OTP saat transit di jaringan. |

## 🧪 Testing

### Testing dengan REST Client

Gunakan file `api_testing.http` untuk testing semua endpoint:

```bash
# Buat user baru di database terlebih dahulu
INSERT INTO users (email, name, is_active, created_at, updated_at)
VALUES ('test@example.com', 'Test User', true, NOW(), NOW());
```

Kemudian gunakan HTTP Client (VS Code, Postman, dll) untuk menjalankan test case yang ada di `api_testing.http`.

### Testing Rate Limiting

```bash
# Kirim 6 request ke email yang sama - request ke-6 akan mendapat 429
for i in {1..6}; do
  curl -X POST http://localhost:8080/api/v1/auth/request-otp \
    -H "Content-Type: application/json" \
    -d '{"email": "test@example.com"}'
  echo ""
done
```

## 📝 Catatan Development

- **Redis Fallback**: Aplikasi tetap berfungsi tanpa Redis menggunakan in-memory storage
- **Email Development Mode**: OTP ditampilkan di console log untuk kemudahan testing
- **Database Migrations**: Schema otomatis dibuat oleh Hibernate (`ddl-auto=update`)
- **Port Configuration**: Default port 8080, gunakan port lain jika terjadi konflik