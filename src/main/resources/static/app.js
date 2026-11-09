/* =======================================================
   CineBook — Frontend Application Logic
   ======================================================= */

// ── State ────────────────────────────────────────────────
let currentMode    = 'login';
let token          = null;
let currentUsername = null;
let selectedSeatIds = new Set();
let currentShowId   = null;
let currentShows    = [];

// ── Bootstrap ────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    token = sessionStorage.getItem('token');
    currentUsername = sessionStorage.getItem('username');

    if (token && currentUsername) {
        enterApp();
    }
});

// ── Auth Tab Switcher ─────────────────────────────────────
function switchTab(mode) {
    currentMode = mode;

    document.getElementById('tab-login').classList.toggle('active', mode === 'login');
    document.getElementById('tab-register').classList.toggle('active', mode === 'register');
    document.getElementById('auth-btn-text').textContent = mode === 'login' ? 'Login' : 'Create Account';

    const emailGroup = document.getElementById('email-group');
    const emailInput = document.getElementById('email');
    if (mode === 'register') {
        emailGroup.classList.remove('hidden');
        emailInput.required = true;
    } else {
        emailGroup.classList.add('hidden');
        emailInput.required = false;
        emailInput.value = '';
    }
}

// ── Auth Submit ───────────────────────────────────────────
async function handleAuth(e) {
    e.preventDefault();

    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const email    = document.getElementById('email').value.trim();
    const btnText  = document.getElementById('auth-btn-text');
    const btnSpinner = document.getElementById('auth-btn-spinner');
    const btn      = document.getElementById('auth-btn');

    btn.disabled = true;
    btnText.classList.add('hidden');
    btnSpinner.classList.remove('hidden');

    try {
        const endpoint = currentMode === 'login' ? '/api/auth/login' : '/api/auth/register';
        const payload  = currentMode === 'login'
            ? { username, password }
            : { username, email, password };

        const res  = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const text = await res.text();
        let data;
        try { data = JSON.parse(text); } catch { data = { message: text }; }

        if (res.ok) {
            token = data.token;
            currentUsername = username;
            sessionStorage.setItem('token', token);
            sessionStorage.setItem('username', currentUsername);
            enterApp();
        } else {
            const msg = data.message || (typeof data === 'string' ? data : 'Authentication failed');
            showToast('error', currentMode === 'login' ? 'Login Failed' : 'Registration Failed', msg);
        }
    } catch (err) {
        showToast('error', 'Network Error', 'Could not connect to the server.');
    } finally {
        btn.disabled = false;
        btnText.classList.remove('hidden');
        btnSpinner.classList.add('hidden');
    }
}

// ── Enter App After Auth ──────────────────────────────────
function enterApp() {
    document.getElementById('auth-screen').classList.add('hidden');
    document.getElementById('app-screen').classList.remove('hidden');

    // Set user display
    document.getElementById('username-display').textContent = currentUsername;
    document.getElementById('user-avatar').textContent = (currentUsername || 'U')[0].toUpperCase();

    // Load shows then default to booking page
    loadShows();
    showPage('booking');
}

// ── Logout ────────────────────────────────────────────────
function logout() {
    token = null;
    currentUsername = null;
    sessionStorage.clear();
    currentShowId = null;
    selectedSeatIds.clear();

    document.getElementById('auth-screen').classList.remove('hidden');
    document.getElementById('app-screen').classList.add('hidden');
    document.getElementById('auth-form').reset();
    showToast('info', 'Logged Out', 'See you next time!');
}

// ── Page Navigation ───────────────────────────────────────
function showPage(page) {
    document.querySelectorAll('.page').forEach(p => p.classList.add('hidden'));
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));

    document.getElementById(`page-${page}`).classList.remove('hidden');
    document.getElementById(`nav-${page === 'booking' ? 'book' : 'my'}`).classList.add('active');

    if (page === 'mybookings') loadMyBookings();
}

// ── Load Shows ────────────────────────────────────────────
async function loadShows() {
    const listEl = document.getElementById('show-list');
    listEl.innerHTML = '<div class="loading-shows">Loading shows...</div>';

    try {
        const res = await fetch('/api/shows');
        if (!res.ok) throw new Error('Failed to fetch shows');
        currentShows = await res.json();
        renderShowList(currentShows);
    } catch (err) {
        listEl.innerHTML = '<div class="loading-shows" style="color:#ef4444">Failed to load shows</div>';
    }
}

function renderShowList(shows) {
    const listEl = document.getElementById('show-list');

    if (!shows.length) {
        listEl.innerHTML = '<div class="loading-shows">No shows available</div>';
        return;
    }

    listEl.innerHTML = '';
    shows.forEach(show => {
        const card = document.createElement('div');
        card.className = 'show-card';
        card.dataset.id = show.id;

        const dt = new Date(show.startTime);
        const dateStr = dt.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

        card.innerHTML = `
            <div class="show-card-title">${escHtml(show.title)}</div>
            <div class="show-card-meta">📍 ${escHtml(show.venue)} · ${dateStr}</div>
        `;
        card.onclick = () => selectShow(show, card);
        listEl.appendChild(card);
    });
}

function selectShow(show, cardEl) {
    // Highlight active show card
    document.querySelectorAll('.show-card').forEach(c => c.classList.remove('active'));
    cardEl.classList.add('active');

    currentShowId = show.id;

    const dt = new Date(show.startTime);
    const dateStr = dt.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' });

    document.getElementById('show-title-display').textContent = show.title;
    document.getElementById('show-details-display').textContent = `📍 ${show.venue}  ·  🕐 ${dateStr}`;
    document.getElementById('refresh-btn').style.display = '';

    // Switch to booking page if not there
    showPage('booking');

    selectedSeatIds.clear();
    updateBookingPanel();
    fetchSeats(show.id);
}

// ── Fetch & Render Seats ──────────────────────────────────
async function fetchSeats(showId) {
    const mapEl = document.getElementById('seat-map');
    const containerEl = document.getElementById('seat-map-container');
    const emptyEl = document.getElementById('empty-state');
    const legendEl = document.getElementById('legend');
    const screenEl = document.getElementById('screen-indicator');

    containerEl.classList.add('hidden');
    emptyEl.classList.remove('hidden');
    legendEl.classList.add('hidden');
    screenEl.classList.add('hidden');
    mapEl.innerHTML = '<div style="grid-column:1/-1;text-align:center;color:var(--text-2);padding:2rem;">Loading seats…</div>';

    try {
        const res = await fetch(`/api/shows/${showId}/seats`);
        if (res.status === 404) {
            emptyEl.classList.remove('hidden');
            containerEl.classList.add('hidden');
            showToast('warning', 'Not Found', 'Show not found.');
            return;
        }
        if (!res.ok) throw new Error('Failed to load seats');

        const seats = await res.json();
        emptyEl.classList.add('hidden');
        containerEl.classList.remove('hidden');
        legendEl.classList.remove('hidden');
        screenEl.classList.remove('hidden');

        renderSeats(seats);
    } catch (err) {
        mapEl.innerHTML = `<div style="grid-column:1/-1;text-align:center;color:var(--red-dim);padding:2rem;">Failed to load seats</div>`;
        containerEl.classList.remove('hidden');
        emptyEl.classList.add('hidden');
    }
}

function renderSeats(seats) {
    const mapEl = document.getElementById('seat-map');
    mapEl.innerHTML = '';

    if (!seats.length) {
        mapEl.innerHTML = '<div style="grid-column:1/-1;text-align:center;color:var(--text-2);padding:2rem;">No seats for this show yet.</div>';
        return;
    }

    seats.forEach(seat => {
        const el = document.createElement('div');
        el.className = 'seat';
        el.textContent = seat.seatNumber;
        el.dataset.id = seat.id;

        if (seat.status === 'BOOKED') {
            el.classList.add('booked');
            el.title = `${seat.seatNumber} — Booked`;
        } else if (seat.status === 'HELD') {
            el.classList.add('held');
            el.title = `${seat.seatNumber} — Held (temporarily reserved)`;
        } else {
            el.title = `${seat.seatNumber} — Available`;
            el.onclick = () => toggleSeat(el, seat.id, seat.seatNumber);
        }

        mapEl.appendChild(el);
    });
}

function toggleSeat(el, seatId, seatNumber) {
    if (el.classList.contains('selected')) {
        el.classList.remove('selected');
        selectedSeatIds.delete(seatId);
    } else {
        el.classList.add('selected');
        selectedSeatIds.add(seatId);
    }
    updateBookingPanel();
}

function updateBookingPanel() {
    const panel = document.getElementById('booking-panel');
    const countEl = document.getElementById('selected-count');
    const chipsEl = document.getElementById('selected-seat-chips');
    const bookBtn = document.getElementById('book-btn');

    const count = selectedSeatIds.size;
    countEl.textContent = count;
    bookBtn.disabled = count === 0;

    // Build chips from selected seat DOM elements
    chipsEl.innerHTML = '';
    document.querySelectorAll('.seat.selected').forEach(seatEl => {
        const chip = document.createElement('span');
        chip.className = 'seat-chip';
        chip.textContent = seatEl.textContent;
        chipsEl.appendChild(chip);
    });

    if (currentShowId) {
        panel.classList.toggle('hidden', count === 0);
    }
}

// ── Refresh Button ────────────────────────────────────────
function refreshSeats() {
    if (!currentShowId) return;
    selectedSeatIds.clear();
    updateBookingPanel();
    fetchSeats(currentShowId);
}

// ── Book Seats ────────────────────────────────────────────
async function bookSeats() {
    if (!selectedSeatIds.size) return;

    const btn       = document.getElementById('book-btn');
    const btnText   = document.getElementById('book-btn-text');
    const btnSpinner = document.getElementById('book-btn-spinner');

    btn.disabled = true;
    btnText.classList.add('hidden');
    btnSpinner.classList.remove('hidden');

    const seatIdsArray = Array.from(selectedSeatIds);

    try {
        const res = await fetch('/api/bookings', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`,
                'Idempotency-Key': generateUUID(),
                'X-Simulate-Delay': 'true'
            },
            body: JSON.stringify({ showId: Number(currentShowId), seatIds: seatIdsArray })
        });

        const text = await res.text();
        let data;
        try { data = JSON.parse(text); } catch { data = { message: text }; }

        if (res.ok) {
            showToast('success', 'Booking Confirmed! 🎉',
                `Booking #${data.bookingId} confirmed. Enjoy the show!`);
            selectedSeatIds.clear();
            updateBookingPanel();
            setTimeout(() => fetchSeats(currentShowId), 1500);
        } else if (res.status === 429) {
            showToast('warning', 'Slow Down!', 'Rate limit exceeded. Please wait a moment.');
        } else {
            const msg = data.message || (typeof data === 'string' ? data : 'Booking failed');
            showToast('error', 'Booking Failed', msg);
            setTimeout(() => fetchSeats(currentShowId), 1500);
        }
    } catch (err) {
        showToast('error', 'Network Error', 'Could not reach the server. Please retry.');
    } finally {
        btn.disabled = false;
        btnText.classList.remove('hidden');
        btnSpinner.classList.add('hidden');
    }
}

// ── Simulate Microsecond Race (0ms gap) ────────────────────
async function simulateRace() {
    if (!selectedSeatIds.size) return;
    const seatId = Array.from(selectedSeatIds)[0];
    showToast('info', 'Race Started! 🏁', `Firing 2 simultaneous requests for Seat ${seatId}...`);

    const sendBooking = (userLabel) => fetch('/api/bookings', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
            'Idempotency-Key': generateUUID()
        },
        body: JSON.stringify({ showId: Number(currentShowId), seatIds: [seatId] })
    }).then(async res => {
        const text = await res.text();
        let data;
        try { data = JSON.parse(text); } catch { data = { message: text }; }
        return { userLabel, status: res.status, data };
    });

    try {
        const [req1, req2] = await Promise.all([sendBooking('Request 1'), sendBooking('Request 2')]);

        if (req1.status === 200) {
            showToast('success', 'Request 1: WON! 🎉', `Booking #${req1.data.bookingId} confirmed`);
        } else {
            showToast('error', 'Request 1: REJECTED', req1.data.message || 'Conflict');
        }

        if (req2.status === 200) {
            showToast('success', 'Request 2: WON! 🎉', `Booking #${req2.data.bookingId} confirmed`);
        } else {
            showToast('error', 'Request 2: REJECTED 🛑', req2.data.message || 'Seat not bookable (409 Conflict)');
        }
    } catch (err) {
        showToast('error', 'Race Error', 'Failed to execute race requests.');
    }

    selectedSeatIds.clear();
    updateBookingPanel();
    setTimeout(() => fetchSeats(currentShowId), 1500);
}

// ── My Bookings ───────────────────────────────────────────
async function loadMyBookings() {
    const listEl = document.getElementById('my-bookings-list');
    listEl.innerHTML = '<div class="loading-placeholder">Loading your bookings…</div>';

    try {
        const res = await fetch('/api/bookings/my', {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!res.ok) throw new Error('Failed to fetch bookings');

        const bookings = await res.json();
        renderMyBookings(bookings);
    } catch (err) {
        listEl.innerHTML = '<div class="loading-placeholder" style="color:var(--red)">Failed to load bookings.</div>';
    }
}

function renderMyBookings(bookings) {
    const listEl = document.getElementById('my-bookings-list');

    if (!bookings.length) {
        listEl.innerHTML = `
            <div class="no-bookings">
                <div style="font-size:2.5rem;margin-bottom:1rem">🎟️</div>
                <p>You have no bookings yet. Head over to <strong>Book Seats</strong> to get started!</p>
            </div>`;
        return;
    }

    listEl.innerHTML = '';
    // Sort newest first
    bookings.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

    bookings.forEach(b => {
        const dt = new Date(b.createdAt);
        const dateStr = dt.toLocaleDateString('en-IN', {
            day: 'numeric', month: 'short', year: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });

        const seatTags = (b.seatNumbers || [])
            .map(s => `<span class="seat-tag">${escHtml(s)}</span>`)
            .join('');

        const item = document.createElement('div');
        item.className = 'booking-item';
        item.innerHTML = `
            <div class="booking-badge">🎬</div>
            <div class="booking-details">
                <div class="booking-show">${escHtml(b.showTitle || 'Show')}</div>
                <div class="booking-seats">${seatTags || '<span style="color:var(--text-3);font-size:.8rem">No seats</span>'}</div>
                <div class="booking-date">Booked on ${dateStr}</div>
            </div>
            <div class="booking-status ${b.status}">${b.status}</div>
        `;
        listEl.appendChild(item);
    });
}

// ── Toast System ──────────────────────────────────────────
function showToast(type, title, msg) {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `
        <div class="toast-title">${escHtml(title)}</div>
        ${msg ? `<div class="toast-msg">${escHtml(msg)}</div>` : ''}
    `;
    container.appendChild(toast);

    // Auto-dismiss after 4 s
    setTimeout(() => {
        toast.classList.add('hiding');
        setTimeout(() => toast.remove(), 320);
    }, 4000);
}

// ── Helpers ───────────────────────────────────────────────
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
        const r = Math.random() * 16 | 0;
        return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    });
}

function escHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
