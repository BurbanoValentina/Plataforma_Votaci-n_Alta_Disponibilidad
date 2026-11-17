// Configuración pública de la aplicación web de Firebase.
// Reemplaza estos valores con los que entrega Firebase Console al registrar tu app web.
// Esta configuración no contiene secretos; las reglas de seguridad se aplican en Firebase y en el backend.
export const firebaseConfig = {
    apiKey: "AIzaSyBHFG-VxlS7cTsNkW6r44eVXV5DU4s_D2M",
    authDomain: "plataformadevotacion.firebaseapp.com",
    projectId: "plataformadevotacion",
    storageBucket: "plataformadevotacion.firebasestorage.app",
    messagingSenderId: "814759896308",
    appId: "1:814759896308:web:e5571f02f4ec4ed2fc2aa1",
    measurementId: "G-07QYP95SS7"
};

export const isFirebaseConfigured = Object.values(firebaseConfig)
    .every((value) => value && !String(value).startsWith("REEMPLAZAR_"));
