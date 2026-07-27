# WhatsApp Web Clone

A full-stack, real-time messaging and VoIP application inspired by WhatsApp Web. Built with React and Spring Boot, this project implements bi-directional WebSocket communication, peer-to-peer WebRTC calling, and offline-first data caching to deliver a native-feeling chat experience.

## 🚀 Comprehensive Feature Set

### 💬 Real-Time Messaging & Chat
* **WebSocket Integration:** Bi-directional real-time communication using SockJS and STOMP protocol.
* **Optimistic UI Updates:** Outgoing messages are injected into the DOM instantly with temporary IDs to ensure zero perceived latency before backend confirmation.
* **Read Receipts Sync:** Live message status tracking (Sent -> Delivered -> Read) represented by dynamic checkmark icons (gray vs. blue).
* **Infinite Scroll Pagination:** Fetches older chat history asynchronously when the user scrolls to the top of the container, maintaining scroll position.
* **Smart Date Dividers:** Automatically groups messages by date ("Today", "Yesterday", or formatted date headers) based on timestamps.
* **File & Media Handling:** Support for sharing images and documents. Includes an integrated lightbox for viewing zoomed-in image attachments.

### 📞 VoIP Audio & Video Calling
* **WebRTC Peer-to-Peer:** Direct browser-to-browser media streaming using `RTCPeerConnection` and Google STUN servers.
* **Custom Signaling Protocol:** Offers, Answers, and ICE Candidates are routed through a dedicated WebSocket topic (`/app/call.signal`).
* **Hardware Lifecycle Management:** Automatically acquires camera/microphone permissions on call initiation and completely releases hardware tracks on hangup/decline.
* **Call Controls:** In-call UI allows users to mute audio or disable video tracks dynamically. Local video is rendered Picture-in-Picture (PiP) style.
* **Call History:** Dedicated view logging incoming, outgoing, and missed calls with timestamps and status-specific iconography.

### 🔍 Search & Performance
* **Lucene Mass Indexing:** Uses Hibernate Search (Apache Lucene) to build and maintain inverted full-text indexes for high-speed, scalable user lookups.
* **Fast User Discovery:** Instant user search across `username`, `firstName`, and `lastName` with fuzzy matching and partial wildcard support.
* **IndexedDB Persistence:** Chat histories are asynchronously stored locally in the browser via `WhatsApp_IndexedDB`. 
* **Offline-First Rendering:** On chat selection, messages are immediately loaded from IndexedDB while the application fetches the delta from the server, resulting in instant load times.

### 🔒 Security & Authentication
* **Client-Side Hashing:** Passwords are mathematically hashed (SHA-256) via the native Web Crypto API *before* transmitting over the network.
* **JWT & Session Management:** Authenticated routes and WebSocket handshakes are secured via Spring Security and HTTP-only credentials.

### 🎨 UI / UX & Responsiveness
* **Adaptive Multi-Pane Layout:** CSS-in-JS combined with dynamic media queries seamlessly transitions the app from a desktop multi-column view to a mobile single-column view with a bottom navigation bar.
* **User Discovery:** Real-time search to find users by username or full name.
* **Profile Management:** Users can upload custom avatars (persisted in backend), view "Last Seen" timestamps, and manage profile details.
* **Custom Dark Theme:** Built from scratch using native WhatsApp Web color palettes (`#0b141a`, `#202c33`, `#005c4b`).

---

## 📷 Screenshots
### 🖥️ Desktop Interface:
* **Login Page:**
<img src="Interface Screenshots/Desktop Interface/Login.png" alt="Login Page" width="500">

* **Registration Page:**
<img src="Interface Screenshots/Desktop Interface/Registration.png" alt="Registration Page" width="500">

* **Chat Page:**
<img src="Interface Screenshots/Desktop Interface/Chat.png" alt="Chat Page" width="500">

* **User Search Page:**
<img src="Interface Screenshots/Desktop Interface/User Search.png" alt="User Search Page" width="500">

* **Call History Page:**
<img src="Interface Screenshots/Desktop Interface/Call History.png" alt="Call History Page" width="500">

* **Profile Page:**
<img src="Interface Screenshots/Desktop Interface/Profile.png" alt="Profile Page" width="500">

### 📱 Mobile Interface:
* **Login Page:**
<img src="Interface Screenshots/Mobile Interface/Login.jpg" alt="Login Page" width="250">

* **Registration Page:**
<img src="Interface Screenshots/Mobile Interface/Registration.jpg" alt="Registration Page" width="250">

* **Home Page:**
<img src="Interface Screenshots/Mobile Interface/Home.jpg" alt="Home Page" width="250">

* **Chat Page:**
<img src="Interface Screenshots/Mobile Interface/Chat.jpg" alt="Chat Page" width="250">

* **User Search Page:**
<img src="Interface Screenshots/Mobile Interface/User Search.jpg" alt="User Search Page" width="250">

* **Call History Page:**
<img src="Interface Screenshots/Mobile Interface/Call History.jpg" alt="Call History Page" width="250">

* **Profile Page:**
<img src="Interface Screenshots/Mobile Interface/Profile.jpg" alt="Profile Page" width="250">

---

## 💻 Tech Stack

### Frontend
* **React.js** (Functional Components, Hooks)
* **WebSockets:** `sockjs-client`, `@stomp/stompjs`
* **Storage:** Native `IndexedDB` API
* **Media:** WebRTC API (`getUserMedia`, `RTCPeerConnection`)
* **Cryptography:** Web Crypto API (`window.crypto.subtle`)
* **Icons:** Lucide React

### Backend
* **Java 26 / Spring Boot**
* **Search Engine:** Hibernate Search / Apache Lucene (Mass Indexing)
* **Spring WebSockets** (STOMP Broker)
* **Spring Security** (Auth/JWT)
* **Spring Data JPA / Hibernate**
* **Database:** PostgreSQL / MySQL

---

## 🛠️ Local Development Setup

### Prerequisites
* Java 26+
* Node.js (v16+)
* PostgreSQL or MySQL

### 1. Backend Configuration
Navigate to the backend directory and configure your database credentials in `application.properties`:

```properties
spring.datasource.url=your_db_url
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.datasource.driver-class-name=your_db_driver_class_name
spring.jpa.properties.hibernate.dialect=your_db_hibernate_dialect # e.g. org.hibernate.dialect.PostgreSQLDialect
file.upload-dir=your_file_storage_location
profile.pic.upload-dir=your_profile_pic_storage_location
default.profile.pic.name=your_default_profile_pic_name_with_extension
spring.jpa.hibernate.ddl-auto=validate
hibernate.search.reindex-on-startup=false
```
*Note: You can add your own domain in `app.cors.allowed-origins`.*

*Note: On first startup ensure `spring.jpa.hibernate.ddl-auto` is set to `create` and `hibernate.search.reindex-on-startup` is set to `true`.*

*Note: It is recommended to change `jwt.access.secret` and `jwt.refresh.secret`.*

### 2. Frontend Configuration
Navigate to the frontend directory, install dependencies, and build with `vite`:

```bash
npm install
vite build
```

*Note: Ensure `vite` config output directory is set to `Java/src/resources/static` or you can manually copy the output of `vite build` to it.*

### 3. Testing WebRTC on Mobile
Run the Spring Boot application. It will default to `http://localhost:8080`.
WebRTC strictly requires a **Secure Context** (`https://` or `localhost`). To test video/audio calls between a computer and a mobile device, you must tunnel your backend through HTTPS.

Using Ngrok:
```bash
ngrok http --host-header=localhost 8080
```

1. Copy the generated `https://...ngrok-free.app` URL.
2. Open this URL on your mobile browser to safely test camera and microphone access.

*Note: Ensure `https://...ngrok-free.app` is added in `app.cors.allowed-origins`.*

---

## 🤝 Contributing
Contributions, issues, and feature requests are welcome. Feel free to check the [issues page](https://github.com/mridulsaha2008/WhatsApp/issues) if you want to contribute.

---
*Developed with ❤️ by [Mridul Saha](https://linkedin.com/in/mridulsaha)*
