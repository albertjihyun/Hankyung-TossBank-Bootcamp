# 🚀 1st Hackathon @ Hankyung × TossBank Bootcamp

> Designing User Flow, Not Just Features

---

## 📌 Overview

This project was built during the **1st Hackathon (March 12, 2026)**.

The goal was to create a service that helps beginner investors navigate overwhelming information by providing:

- 📊 Market insights
- 🧠 Personalized investment recommendations

Rather than simply presenting data, the focus was on **designing a user flow** that naturally guides users toward meaningful outcomes.

---

## 🎯 Problem & Idea

Beginner investors often struggle not because of a lack of information,  
but because there is **too much information without clear direction**.

### 💡 Solution

Instead of listing features or pages,  
I designed the service around **two clear user flows**:

1. **Market Insight Flow**
   - Investors → News → Themes → Popular Products

2. **Personalized Recommendation Flow**
   - MBTI Test → Diagnosis → Recommended Products

👉 The goal was to let users **naturally reach the intended outcome without being forced**.

---

## 🧩 Key Features

### 📊 Market Insight
- Investor trend (Individual / Foreign / Institutional)
- Financial news
- Top stock themes
- Popular investment products

### 🧠 Personalized Investment
- Investment MBTI test
- Result analysis
- Customized product recommendations

---

## 🏗️ System Architecture
```
Client (Browser)
    ↓
Cloudflare Tunnel
    ↓
Express Server (Colab)
    ↓
HTML (templates)
    ↓
Static JS Data (/static)
    ↓
Client-side Rendering
```
---

## ⚙️ Tech Stack

- Frontend: HTML, CSS, Vanilla JavaScript
- Backend: Express (Node.js)
- Environment: Google Colab
- Deployment: Cloudflare Tunnel
- State Management: localStorage

---

## 🔄 How It Works

1. User starts from the main page
2. Chooses either:
   - Market exploration
   - Investment MBTI test
3. If MBTI flow:
   - Answers questions
   - Gets a result
   - Data stored in localStorage
4. Moves to:
   - Diagnosis page
   - Personalized recommendation page

👉 No backend database — flow is handled entirely on the client side.

---

## 💡 What I Focused On

Instead of just building features,  
I focused on:

- Designing user behavior flow
- Reducing unnecessary choices
- Creating a natural progression between pages
- Maintaining consistent UI structure

---

## ⚠️ Limitations

- Some data is hardcoded in HTML
- Fallback data duplicated across files
- Repeated layout (no componentization)
- No API / real-time data

---

## 🔧 Future Improvements

- Centralize all data into single JS sources
- Extract common layout (header, sidebar)
- Replace static data with API
- Add backend & database
- Improve recommendation logic

---

## 🪞 Retrospective

This was my first hackathon, and I faced challenges with time management and unfamiliar technologies.

However, I was able to complete the project within the given time, which was my initial goal.

Through this experience, I clearly saw where I can do better.

Most importantly, I realized:

Designing user flow is more important than simply implementing features.

I look forward to building a more refined project in the next hackathon 🚀

---

## 🔗 Links

- GitHub: https://github.com/albertjihyun/1st_Hackerton  
- YouTube (Demo of actual page flow): https://youtu.be/cXFTRYnkE-M
