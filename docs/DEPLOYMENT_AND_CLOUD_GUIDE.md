# ClassDrop — Cloud Deployment, Tunneling & Database Guide

**Author**: Farhan Sayed — KJSIM MCA  
**Project**: ClassDrop — Classroom File & Code Snippet Sharing Hub  
**Stack**: Java 17+ · JDK `HttpServer` · MySQL 8 JDBC · Vanilla Web Client  

---

## 1. Overview of Deployment Strategies

ClassDrop was fundamentally designed as an **offline classroom hub** (Mode 1: LAN, Mode 2: Hotspot). However, to allow users outside the classroom or across the internet to access ClassDrop, there are two primary cloud architectures:

```
+-----------------------------------------------------------------------------------+
| APPROACH A: HYBRID CLOUD TUNNEL (Recommended for Presenter / Mini Project)        |
| - Host laptop runs ClassDrop Swing GUI & stores files locally.                   |
| - Cloudflare Tunnel or ngrok gives a secure, public HTTPS URL (e.g. https://xyz)  |
| - Zero hosting cost, no cloud VM needed, keeps physical host dashboard.           |
+-----------------------------------------------------------------------------------+

+-----------------------------------------------------------------------------------+
| APPROACH B: DEDICATED CLOUD VPS / CONTAINER (Full 24/7 Cloud Hosting)             |
| - Hosted on a Linux cloud server (AWS EC2 / DigitalOcean / Render / Railway).     |
| - ClassDrop runs in Headless Mode (`--headless`) without Swing GUI.              |
| - Web client is publicly accessible by anyone 24/7.                               |
| - Cloud MySQL instance (AWS RDS / Aiven / PlanetScale) persists data.             |
+-----------------------------------------------------------------------------------+
```

---

## 2. Database Connection Guide (Local & Cloud MySQL)

ClassDrop uses standard JDBC (`mysql-connector-j:8.4.0`) to store session audit metadata, connected devices, transfer history, and code snippets.

### 2.1 Database Schema Initialization
Whether connecting to local MySQL or a cloud-hosted MySQL instance, run [schema.sql](file:///D:/ShareDrop/schema.sql) once to create the tables:

```sql
CREATE DATABASE IF NOT EXISTS classdrop;
USE classdrop;
-- Tables: sessions, devices, transfers, snippets
```

**Using MySQL CLI:**
```bash
mysql -h <host> -u <user> -p < schema.sql
```

**Using MySQL Workbench / DBeaver / phpMyAdmin:**
Open [schema.sql](file:///D:/ShareDrop/schema.sql) and click **Execute All (Ctrl+Shift+Enter)**.

---

### 2.2 Configuring Database Credentials

Configuration is managed by `Config.java` reading from `application.properties`.

#### Method 1: Editing `src/main/resources/application.properties` (Build-time)
```properties
server.port=8080
max.file.size.bytes=104857600
thread.pool.size=16
session.timeout.minutes=120
cleanup.interval.seconds=30

# MySQL Configuration
db.url=jdbc:mysql://localhost:3306/classdrop
db.user=root
db.password=your_mysql_password
```

#### Method 2: External `application.properties` (Runtime Override — Recommended for Cloud)
Place an `application.properties` file in the same directory where you run the JAR. ClassDrop prioritizes the external file over classpath defaults:
```properties
db.url=jdbc:mysql://your-cloud-db-host.com:3306/classdrop?useSSL=true&serverTimezone=UTC
db.user=admin
db.password=SecretCloudPassword123!
```

---

### 2.3 Recommended Free Cloud MySQL Providers

If deploying to the cloud without running local MySQL:
1. **Aiven for MySQL** (Free tier: 1 CPU, 1 GB RAM, 5 GB storage, SSL included) — [https://aiven.io](https://aiven.io)
2. **Clever Cloud** (Free 20 MB MySQL add-on for student projects) — [https://clever-cloud.com](https://clever-cloud.com)
3. **Railway.app** ($5 free credit monthly for managed MySQL) — [https://railway.app](https://railway.app)

*Example Cloud Connection String:*
```properties
db.url=jdbc:mysql://mysql-example-host.com:12345/defaultdb?sslMode=REQUIRED
db.user=avnadmin
db.password=your_cloud_password_here
```

> **Built-in Fallback**: If the database is unreachable or credentials fail, ClassDrop automatically degrades to in-memory mode without crashing.

---

## 3. Approach A: Hybrid Cloud Tunnel via Cloudflare Tunnel (Zero Cost)

This is the **best and easiest method** for student demonstrations: you run the application on your laptop with the full Swing GUI, but anyone on the internet can join via a public HTTPS link.

### Step 1: Install Cloudflare Tunnel (`cloudflared`)
Download `cloudflared` for Windows:
```powershell
winget install --id Cloudflare.cloudflared
```
*(Or download the binary from [Cloudflare Releases](https://github.com/cloudflare/cloudflared/releases)).*

### Step 2: Start ClassDrop
```powershell
java -jar target/classdrop-1.0.0.jar
```

### Step 3: Launch Quick Tunnel (No domain needed)
Open a second PowerShell window and run:
```powershell
cloudflared tunnel --url http://localhost:8080
```

Cloudflare will output a public HTTPS URL:
```
+-----------------------------------------------------------------------+
| Your quick Tunnel has been created! Visit it at:                     |
| https://random-generated-words.trycloudflare.com                      |
+-----------------------------------------------------------------------+
```

### Step 4: Share with Anyone
Anyone in the world can open `https://random-generated-words.trycloudflare.com` on their mobile phone or PC, enter the 6-digit room code from your Swing dashboard, and upload/download files directly to/from your laptop!

---

## 4. Approach B: Full Cloud VPS / Docker Deployment (24/7 Hosting)

For hosting ClassDrop 24/7 on a cloud virtual server (e.g. AWS EC2, DigitalOcean, Oracle Cloud, Render).

### 4.1 Dockerfile
Create a `Dockerfile` in the project root:

```dockerfile
# Stage 1: Build JAR with Maven & OpenJDK 17
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
COPY schema.sql .
RUN mvn clean package -DskipTests

# Stage 2: Minimal JRE Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create staging storage directory
RUN mkdir -p /app/data

# Copy built fat JAR
COPY --from=build /app/target/classdrop-1.0.0.jar app.jar
COPY schema.sql .

EXPOSE 8080

# Run in headless CLI mode for server environments
ENTRYPOINT ["java", "-jar", "app.jar", "--headless"]
```

### 4.2 Docker Compose (`docker-compose.yml`)
To run both ClassDrop and MySQL 8 together in cloud:

```yaml
version: '3.8'

services:
  db:
    image: mysql:8.4
    container_name: classdrop-mysql
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
      MYSQL_DATABASE: classdrop
    ports:
      - "3306:3306"
    volumes:
      - db_data:/var/lib/mysql
      - ./schema.sql:/docker-entrypoint-initdb.d/schema.sql

  app:
    build: .
    container_name: classdrop-server
    restart: always
    ports:
      - "8080:8080"
    environment:
      DB_URL: "jdbc:mysql://db:3306/classdrop"
      DB_USER: "root"
      DB_PASSWORD: "rootpassword"
    volumes:
      - app_data:/app/data
    depends_on:
      - db

volumes:
  db_data:
  app_data:
```

### 4.3 Running with Docker
```bash
# Build and start both containers in background
docker compose up -d

# Check logs
docker compose logs -f app
```

---

## 5. Nginx Reverse Proxy with SSL (Domain & HTTPS)

When hosting on a Linux VPS (e.g., AWS EC2 Ubuntu), put Nginx in front of port 8080 to handle HTTPS and custom domains.

### Nginx Configuration (`/etc/nginx/sites-available/classdrop`)
```nginx
server {
    server_name classdrop.yourdomain.com;

    client_max_body_size 120M; # Accommodate 100 MB file uploads

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Disable proxy buffering for chunked streaming
        proxy_buffering off;
        proxy_request_buffering off;
        proxy_read_timeout 600s;
        proxy_send_timeout 600s;
    }
}
```

### Enable SSL with Free Let's Encrypt Certbot
```bash
sudo certbot --nginx -d classdrop.yourdomain.com
```

---

## 6. What Is Needed From You (Input Checklist)

Depending on your target deployment, here is what you need to prepare:

### For Classroom Presentation (Offline / Local):
- [ ] No cloud accounts needed.
- [ ] Windows Mobile Hotspot enabled.
- [ ] Laptop running Java 17+.

### For Cloudflare Tunnel (Remote Friends / Live Web Demo):
- [ ] Download `cloudflared.exe`.
- [ ] No API keys, no card, no domain required.
- [ ] Command: `cloudflared tunnel --url http://localhost:8080`.

### For Cloud Production Server (VPS / Cloud VM):
- [ ] **Cloud VPS**: AWS EC2 / DigitalOcean Droplet ($4–$6/mo) or Oracle Cloud Always Free VM.
- [ ] **Domain Name**: e.g., from Namecheap, Cloudflare Registrar, or DuckDNS (free dynamic DNS).
- [ ] **Cloud MySQL**: Local Docker MySQL or managed database (Aiven / PlanetScale).
- [ ] **Firewall**: Open TCP ports `80` (HTTP), `443` (HTTPS), and `8080` (Direct HTTP).
