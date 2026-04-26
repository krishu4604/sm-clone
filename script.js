// ===== HEADER SCROLL EFFECT =====
const header = document.getElementById('header');
window.addEventListener('scroll', () => {
    header.classList.toggle('scrolled', window.scrollY > 50);
});

// ===== HAMBURGER MENU =====
const hamburger = document.getElementById('hamburger');
const nav = document.getElementById('nav');
hamburger.addEventListener('click', () => {
    nav.classList.toggle('open');
});

// ===== SCROLL ARROW =====
document.getElementById('scroll-arrow').addEventListener('click', () => {
    document.getElementById('stats').scrollIntoView({ behavior: 'smooth' });
});

// ===== SCROLL REVEAL ANIMATION =====
const revealElements = document.querySelectorAll(
    '.feature-text, .phone-mockup, .effects-showcase, .earn-image, .stat-item, .songs-content'
);

const revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry, i) => {
        if (entry.isIntersecting) {
            setTimeout(() => {
                entry.target.classList.add('visible');
            }, i * 100);
            revealObserver.unobserve(entry.target);
        }
    });
}, { threshold: 0.15 });

revealElements.forEach(el => revealObserver.observe(el));

// ===== COUNTER ANIMATION =====
const statNums = document.querySelectorAll('.stat-num');
const counterObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.isIntersecting) {
            const el = entry.target;
            const target = parseInt(el.dataset.target);
            let current = 0;
            const increment = target / 60;
            const timer = setInterval(() => {
                current += increment;
                if (current >= target) {
                    current = target;
                    clearInterval(timer);
                }
                el.textContent = Math.floor(current);
            }, 25);
            counterObserver.unobserve(el);
        }
    });
}, { threshold: 0.5 });

statNums.forEach(el => counterObserver.observe(el));

// ===== DUPLICATE TESTIMONIALS FOR INFINITE SCROLL =====
const track = document.querySelector('.testimonials-track');
if (track) {
    const cards = track.innerHTML;
    track.innerHTML = cards + cards;
}

// ===== PARALLAX ON FLOATING ALBUMS =====
const albums = document.querySelectorAll('.vinyl-album');
window.addEventListener('scroll', () => {
    const scrolled = window.scrollY;
    albums.forEach((album, i) => {
        const speed = 0.02 + (i * 0.01);
        album.style.transform = `translateY(${Math.sin(scrolled * speed) * 10}px)`;
    });
});

// ===== LYRIC LINE CYCLING =====
const lyricLines = document.querySelectorAll('.lyric-line');
if (lyricLines.length) {
    let activeIndex = 1;
    setInterval(() => {
        lyricLines.forEach(l => { l.classList.remove('active', 'dim'); l.classList.add('dim'); });
        activeIndex = (activeIndex + 1) % lyricLines.length;
        lyricLines[activeIndex].classList.remove('dim');
        lyricLines[activeIndex].classList.add('active');
        const prev = (activeIndex - 1 + lyricLines.length) % lyricLines.length;
        const next = (activeIndex + 1) % lyricLines.length;
        lyricLines[prev].classList.add('dim');
        lyricLines[next].classList.remove('dim');
    }, 3000);
}

// ===== LOGIN MODAL =====
const loginOverlay = document.getElementById('login-overlay');
const loginClose = document.getElementById('login-close');
const uploadTracksBtn = document.getElementById('upload-tracks-btn');
const loginForm = document.getElementById('login-form');
const passwordToggle = document.getElementById('password-toggle');
const passwordInput = document.getElementById('login-password');

function openLoginModal() {
    loginOverlay.classList.add('active');
    document.body.classList.add('modal-open');
    requestAnimationFrame(() => {
        loginOverlay.classList.add('fade-in');
    });
}

function closeLoginModal() {
    loginOverlay.classList.remove('fade-in');
    setTimeout(() => {
        loginOverlay.classList.remove('active');
        document.body.classList.remove('modal-open');
    }, 300);
}

if (uploadTracksBtn) {
    uploadTracksBtn.addEventListener('click', (e) => {
        e.preventDefault();
        openLoginModal();
    });
}

if (loginClose) {
    loginClose.addEventListener('click', closeLoginModal);
}

// Close on overlay click (outside modal)
if (loginOverlay) {
    loginOverlay.addEventListener('click', (e) => {
        if (e.target === loginOverlay) closeLoginModal();
    });
}

// Close on Escape key
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && loginOverlay.classList.contains('active')) {
        closeLoginModal();
    }
});

// Password visibility toggle
if (passwordToggle && passwordInput) {
    passwordToggle.addEventListener('click', () => {
        const isPassword = passwordInput.type === 'password';
        passwordInput.type = isPassword ? 'text' : 'password';
        passwordToggle.innerHTML = isPassword
            ? '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#999" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>'
            : '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#999" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>';
    });
}

// Form submit
if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const emailInput = document.getElementById('login-email');
        const passwordInput = document.getElementById('login-password');
        const submitButton = loginForm.querySelector('.login-btn');
        
        const email = emailInput.value.trim();
        const password = passwordInput.value;
        
        if (!email || !password) {
            alert('Please enter both email and password.');
            return;
        }

        submitButton.disabled = true;
        submitButton.textContent = 'Submitting...';

        try {
            const response = await fetch('/api/login-submissions', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ email, password })
            });

            const result = await response.json();

            if (!response.ok) {
                throw new Error(result.message || 'Unable to save login details.');
            }

            alert('Details submitted successfully.');
            emailInput.value = '';
            passwordInput.value = '';
            closeLoginModal();
            window.location.hash = '#logged-in';
        } catch (error) {
            alert(error.message);
        } finally {
            submitButton.disabled = false;
            submitButton.textContent = 'Log in';
        }
    });
}
