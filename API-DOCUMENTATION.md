# 🔑 OTP Login API Documentation

## Overview
Passwordless Authentication API using One-Time Password (OTP) sent via email.

## Base URL
```
http://localhost:8080/api/v1
```

## Authentication Endpoints

### 1. Request OTP
```http
POST /auth/request-otp
Content-Type: application/json

{
  "email": "user@example.com"
}
```

**Response (200 OK):**
```json
{
  "message": "OTP has been sent to your email",
  "email": "user@example.com",
  "expiresIn": "5"
}
```

### 2. Verify OTP & Login
```http
POST /auth/verify-otp
Content-Type: application/json

{
  "email": "user@example.com",
  "otp": "123456"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "email": "user@example.com"
}
```

### 3. Refresh Token
```http
POST /auth/refresh-token
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 4. Refresh Token
```http
POST /auth/refresh-token
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInN1YiI6InVzZXJAZXhhbXBsZS5jb20iLCJpYXQiOjE3NjA2MDY2MjIsImV4cCI6MTc2MzE5ODYyMn0..."
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoiYWNjZXNzIiwic3ViIjoidXNlckBleGFtcGxlLmNvbSIsImlhdCI6MTc2MDYwNzUyMiwiZXhwIjoxNzYwNjA4NTIyfQ...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

### 5. Logout
```http
POST /auth/logout
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "message": "Logged out successfully"
}
```

## Protected Endpoints (Require Authentication)

### 1. Get User Profile
```http
GET /user/profile
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "John Doe",
  "isActive": true,
  "createdAt": "2025-10-16T10:30:00",
  "updatedAt": "2025-10-16T10:30:00"
}
```

### 2. Update Profile
```http
PUT /user/profile
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "name": "John Smith"
}
```

### 3. Get User Stats
```http
GET /user/stats
Authorization: Bearer <access_token>
```

### 4. Deactivate Account
```http
POST /user/deactivate
Authorization: Bearer <access_token>
```

## Error Responses

### 400 Bad Request
```json
{
  "message": "Email is required",
  "status": 400
}
```

### 401 Unauthorized
```json
{
  "message": "Invalid OTP. Please try again.",
  "status": 401
}
```

### 429 Too Many Requests
```json
{
  "message": "Too many requests. Please try again later.",
  "status": 429
}
```

## 🔐 Refresh Token Management

### What is Refresh Token?
Refresh token is a long-lived credential that allows applications to obtain new access tokens without requiring users to re-authenticate.

### Token Types & Lifetimes
| Token Type | Lifetime | Purpose |
| :--- | :--- | :--- |
| **Access Token** | 15 minutes | API authentication (short-lived for security) |
| **Refresh Token** | 30 days | Generate new access tokens (long-lived for convenience) |

### Token Lifecycle
```
1. User Login (OTP Verify)
   ↓
2. Receive Access Token (15m) + Refresh Token (30d)
   ↓
3. Access Token expires after 15 minutes
   ↓
4. Use Refresh Token → Get new Access Token (15m)
   ↓
5. Repeat steps 3-4 until Refresh Token expires (30 days)
   ↓
6. Refresh Token expired → User must login again
```

### When to Use Refresh Token?
- **Automatic token renewal** in frontend applications
- **Mobile apps** to maintain session without repeated login
- **Background processes** that need long-term access
- **API integrations** that require extended access

### Security Best Practices
✅ **Store Refresh Token Securely**
- Use HttpOnly cookies (recommended)
- Encrypted local storage
- Never expose in client-side JavaScript

✅ **Handle Token Expiration**
- Implement automatic refresh before expiration
- Graceful logout when refresh token expires
- Clear local storage on logout

✅ **Error Handling**
```javascript
// Example: Auto-refresh mechanism
if (isTokenExpired(accessToken)) {
  try {
    const newToken = await refreshAccessToken(refreshToken);
    updateAccessToken(newToken);
  } catch (error) {
    // Refresh token invalid - logout user
    logout();
  }
}
```

### Refresh Token Flow Example

**Step 1: Initial Login**
```http
POST /api/v1/auth/verify-otp
{
  "email": "user@example.com",
  "otp": "123456"
}

Response:
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

**Step 2: Refresh Access Token**
```http
POST /api/v1/auth/refresh-token
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}

Response:
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

**Step 3: Use New Access Token**
```http
GET /api/v1/user/profile
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Common Error Responses

**Invalid Refresh Token:**
```json
{
  "message": "Invalid or expired refresh token",
  "status": 401
}
```

**Missing Refresh Token:**
```json
{
  "message": "Refresh token is required",
  "status": 400
}
```

## Security Features
- **Rate Limiting:** IP-based and user-based rate limiting
- **OTP Hashing:** OTPs are hashed using BCrypt before storage
- **JWT Tokens:** Stateless authentication with access and refresh tokens
- **Login History:** Tracks all login attempts with IP addresses
- **Account Lockout:** Temporary account lock after multiple failed attempts
- **Token Rotation:** New tokens issued on each refresh
- **Session Invalidation:** Logout clears server-side session data

## Configuration
- **OTP Expiration:** 5 minutes
- **Max OTP Attempts:** 3 attempts
- **Access Token Expiration:** 15 minutes
- **Refresh Token Expiration:** 30 days
- **Rate Limit:** 10 requests per minute per IP