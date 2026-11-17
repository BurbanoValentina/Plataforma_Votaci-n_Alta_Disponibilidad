import { initializeApp } from "https://www.gstatic.com/firebasejs/13.3.0/firebase-app.js";
import {
    getAuth,
    onAuthStateChanged,
    signOut
} from "https://www.gstatic.com/firebasejs/13.3.0/firebase-auth.js";
import { firebaseConfig, isFirebaseConfigured } from "./firebase-config.js";

const currentPage = window.location.pathname.split("/").pop() || "index.html";
const publicPages = new Set(["login.html", ""]);

if (!isFirebaseConfigured) {
    console.warn("Firebase no está configurado. Completa frontend/html/firebase-config.js para activar el inicio de sesión.");
} else {
    const app = initializeApp(firebaseConfig);
    const auth = getAuth(app);

    onAuthStateChanged(auth, (user) => {
        if (!user && !publicPages.has(currentPage)) {
            window.location.replace("login.html");
            return;
        }

        if (user && currentPage === "login.html") {
            window.location.replace("index.html");
            return;
        }

        if (user && !publicPages.has(currentPage)) {
            addSessionBadge(user, auth);
        }
    });
}

function addSessionBadge(user, auth) {
    if (document.getElementById("firebase-session-badge")) return;

    const badge = document.createElement("div");
    badge.id = "firebase-session-badge";
    badge.innerHTML = `<span>${user.email || "Sesión activa"}</span><button type="button">Cerrar sesión</button>`;
    badge.querySelector("button").addEventListener("click", () => signOut(auth));
    document.body.appendChild(badge);
}
