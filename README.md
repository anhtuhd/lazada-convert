# Lazada Affiliate Link Converter

A lightweight **Java 21 + Spring Boot 3** REST API that converts a Lazada product URL into a tracked **affiliate short link** by calling the Lazada Open Platform API.

---

## ⚡ Quick Start (Local)

### Prerequisites
- Java 21+
- Maven 3.9+
- Lazada Open Platform credentials (`app_key`, `app_secret`, `access_token`)

### 1. Clone & Configure

```bash
cd lazada-affiliate-converter
cp .env.example .env
# Edit .env with your real Lazada credentials
```

### 2. Run

```bash
# Using Maven wrapper
./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="\
    -DLAZADA_APP_KEY=your_key \
    -DLAZADA_APP_SECRET=your_secret \
    -DLAZADA_ACCESS_TOKEN=your_token"
```

Or set environment variables first:
```bash
export LAZADA_APP_KEY=your_key
export LAZADA_APP_SECRET=your_secret
export LAZADA_ACCESS_TOKEN=your_token
./mvnw spring-boot:run
```

---

## 📡 API Reference

### Convert Product Link → Affiliate Link

```
POST /api/convert
Content-Type: application/json
```

**Request Body:**
```json
{
  "productUrl": "https://www.lazada.vn/products/example-i123456789-s987654321.html",
  "subId1": "my-campaign",
  "subId2": "facebook",
  "subId3": "video-ad"
}
```

| Field | Required | Description |
|---|---|---|
| `productUrl` | ✅ | Valid Lazada product URL |
| `subId1` | ❌ | Tracking label (campaign, etc.) |
| `subId2` | ❌ | Tracking label (channel, etc.) |
| `subId3` | ❌ | Tracking label (content, etc.) |

**Response (Success):**
```json
{
  "success": true,
  "affiliateLink": "https://s.lazada.vn/s.xxxxxx",
  "originalUrl": "https://www.lazada.vn/products/...",
  "errorMessage": null,
  "requestId": "abc123"
}
```

**Response (Failure):**
```json
{
  "success": false,
  "affiliateLink": null,
  "originalUrl": "https://www.lazada.vn/products/...",
  "errorMessage": "Lazada error [30]: Invalid access token",
  "requestId": "abc123"
}
```

### Health Check

```
GET /api/health
→ 200 OK "OK"
```

---

## 🐳 Deploy with Docker

### Build & Run locally

```bash
docker build -t lazada-affiliate-converter .

docker run -p 8080:8080 \
  -e LAZADA_APP_KEY=your_key \
  -e LAZADA_APP_SECRET=your_secret \
  -e LAZADA_ACCESS_TOKEN=your_token \
  lazada-affiliate-converter
```

### Test with curl

```bash
curl -X POST http://localhost:8080/api/convert \
  -H "Content-Type: application/json" \
  -d '{
    "productUrl": "https://www.lazada.vn/products/example-i123-s456.html"
  }'
```

---

## ☁️ Free Hosting Options

| Platform | RAM | Always-On | Recommended |
|---|---|---|---|
| **Render** | 512MB | ❌ (sleeps after 15min) | ✅ Easiest Docker deploy |
| **Railway** | 512MB | ✅ | ✅ No config needed |
| **Fly.io** | 256MB | ✅ | ⚠️ Needs JVM tuning |
| **Oracle Cloud Free** | 24GB | ✅ | ⭐ Best for production |

### Deploy to Render
1. Push repo to GitHub
2. Create a **Web Service** on [render.com](https://render.com), select your repo
3. Choose **Docker** as runtime
4. Add environment variables: `LAZADA_APP_KEY`, `LAZADA_APP_SECRET`, `LAZADA_ACCESS_TOKEN`
5. Deploy 🚀

---

## 🔐 How Authentication Works

This tool implements the **Lazada Open Platform signing spec** from scratch (no SDK jar required):

1. Collect all params (system + business)
2. Sort by key (ASCII order)
3. Concatenate as `key1value1key2value2...`
4. Prepend API path: `/affiliate/generateShortLink` + concatenated string
5. Sign with **HMAC-SHA256** using `app_secret` as key
6. Uppercase hex encode → `sign` parameter

---

## 🌏 Gateway URLs by Region

| Country | Gateway URL |
|---|---|
| Vietnam | `https://api.lazada.com/rest` |
| Singapore | `https://api.lazada.sg/rest` |
| Malaysia | `https://api.lazada.com.my/rest` |
| Thailand | `https://api.lazada.co.th/rest` |
| Philippines | `https://api.lazada.com.ph/rest` |
| Indonesia | `https://api.lazada.co.id/rest` |

Set `LAZADA_GATEWAY_URL` env var to change region.
