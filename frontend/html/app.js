let catalog = null;
let selectedElection = 'NACIONAL';
let selectedCandidateId = null;
const regionMultipliers = { NORTE: 1.25, SUR: 1.15, CENTRO: 1.40, ESTE: 0.90, OESTE: 1.05 };

async function initPortal() {
    try {
        const res = await fetch('/api/catalog');
        catalog = await res.json();

        document.getElementById('activeNodeLabel').textContent = `Nodo: ${catalog.nodeId} (${catalog.availabilityZone})`;
        updateLifecycleBadges(catalog.lifecycleState);
        populateCities(catalog.cities);
        renderBallot();
    } catch (e) {
        document.getElementById('activeNodeLabel').textContent = 'Sin conexión al API';
    }

    fetchLiveGlobal();
    connectLiveStream();
}

function updateLifecycleBadges(state) {
    const heroEl = document.getElementById('heroLifecycle');
    const badge = document.getElementById('stateBadge');
    heroEl.textContent = state || 'HABILITADO';
    badge.textContent = state || 'HABILITADO';
    badge.className = 'badge ' + (state === 'HABILITADO' ? 'badge-ok' : state === 'EN_AUDITORIA' ? 'badge-warn' : 'badge-danger');
}

function populateCities(cities) {
    const sel = document.getElementById('citySelect');
    sel.innerHTML = '';
    cities.forEach(item => {
        const opt = document.createElement('option');
        opt.value = item.city;
        opt.dataset.region = item.region;
        opt.textContent = `${item.city} (${item.region})`;
        sel.appendChild(opt);
    });
    onCityChanged();
}

function onCityChanged() {
    const sel = document.getElementById('citySelect');
    const opt = sel.options[sel.selectedIndex];
    if (!opt) return;
    const region = opt.dataset.region || 'CENTRO';
    const mult = regionMultipliers[region] || 1.0;
    document.getElementById('regionDisplay').value = `Región ${region}`;
    document.getElementById('regionWeightHint').textContent =
        `Ponderación regional GoF Strategy: x${mult.toFixed(2)} (Routing Key: results.${region.toLowerCase()})`;
}

function selectElection(type) {
    selectedElection = type;
    selectedCandidateId = null;
    document.getElementById('tabNacional').classList.toggle('active', type === 'NACIONAL');
    document.getElementById('tabRegional').classList.toggle('active', type === 'REGIONAL');
    document.getElementById('ballotTitle').textContent =
        type === 'NACIONAL'
            ? '2. Seleccione su Candidato — Elección Nacional 2026 *'
            : '2. Seleccione su Candidato — Elección Regional 2026 *';
    renderBallot();
}

function renderBallot() {
    if (!catalog) return;
    const candidates = selectedElection === 'NACIONAL' ? catalog.nationalCandidates : catalog.regionalCandidates;
    const grid = document.getElementById('ballotGrid');
    grid.innerHTML = '';

    candidates.forEach(c => {
        const card = document.createElement('div');
        card.className = 'candidate-card' + (selectedCandidateId === c.id ? ' selected' : '');
        card.onclick = () => {
            selectedCandidateId = c.id;
            renderBallot();
        };
        card.innerHTML = `
            <span class="selected-check">✓ Elegido</span>
            <div class="ballot-num" style="background: ${c.accentColor}">#${c.ballotNumber}</div>
            <div class="candidate-name">${c.name}</div>
            <div class="candidate-party">${c.party}</div>
            <div class="candidate-slogan">${c.slogan}</div>
        `;
        grid.appendChild(card);
    });
}

async function submitCitizenVote(e) {
    e.preventDefault();
    const feedback = document.getElementById('voteFeedback');
    const ticketBox = document.getElementById('ticketContainer');
    feedback.innerHTML = '';
    ticketBox.innerHTML = '';

    const cedula = document.getElementById('cedulaInput').value.trim();
    const voterName = document.getElementById('voterNameInput').value.trim();
    const citySel = document.getElementById('citySelect');
    const city = citySel.value;
    const region = citySel.options[citySel.selectedIndex]?.dataset.region || 'CENTRO';

    if (!selectedCandidateId) {
        feedback.innerHTML = `<div class="alert-banner alert-warn">Por favor seleccione un candidato en el tarjetón antes de emitir su voto.</div>`;
        return;
    }

    const btn = document.getElementById('submitVoteBtn');
    btn.disabled = true;
    btn.textContent = 'Verificando cédula y registrando voto...';

    try {
        const res = await fetch('/api/vote', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                voterId: cedula,
                voterName: voterName || null,
                candidateId: selectedCandidateId,
                city,
                region,
                electionType: selectedElection,
                voteSource: 'REAL'
            })
        });

        const data = await res.json();
        if (data.nodeId) {
            document.getElementById('activeNodeLabel').textContent = `Atendido por: ${data.nodeId}`;
        }

        if (res.ok && data.ticket) {
            feedback.innerHTML = `
                <div class="alert-banner alert-success">
                    ✔ Voto verificado y registrado exitosamente en Kafka &amp; PostgreSQL. Su comprobante oficial se muestra abajo.
                </div>`;
            ticketBox.innerHTML = renderTicketHtml(data.ticket, false);
        } else if (res.status === 409 && data.ticket) {
            feedback.innerHTML = `
                <div class="alert-banner alert-warn">
                    ⚠ <strong>Voto Duplicado Bloqueado:</strong> ${escapeHtml(data.message)}
                </div>`;
            ticketBox.innerHTML = renderTicketHtml(data.ticket, true);
        } else {
            feedback.innerHTML = `
                <div class="alert-banner alert-error">
                    ✖ [HTTP ${res.status}] ${escapeHtml(data.message || 'No fue posible registrar el voto.')}
                </div>`;
        }
    } catch (err) {
        feedback.innerHTML = `<div class="alert-banner alert-error">Error de red al contactar el balanceador AWS: ${escapeHtml(err.message)}</div>`;
    } finally {
        btn.disabled = false;
        btn.textContent = 'Verificar Cédula y Emitir Voto Oficial';
    }
}

function renderTicketHtml(ticket, isDuplicate) {
    const otherElection = ticket.electionType === 'NACIONAL' ? 'REGIONAL' : 'NACIONAL';
    const otherLabel = otherElection === 'REGIONAL' ? 'Votar en Elección Regional' : 'Votar en Elección Nacional';
    const votedTime = ticket.votedAt ? new Date(ticket.votedAt).toLocaleString('es-CO') : 'Reciente';

    return `
        <div class="ticket-box ${isDuplicate ? 'duplicate' : ''}">
            <div class="ticket-header">
                <div>
                    <div style="font-size: 0.74rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.06em; color: ${isDuplicate ? '#b45309' : '#047857'};">
                        ${isDuplicate ? 'COMPROBANTE EXISTENTE — YA EJERCIÓ SU VOTO' : 'CERTIFICADO ELECTORAL DIGITAL VERIFICADO'}
                    </div>
                    <div style="font-size: 1.05rem; font-weight: 800; margin-top: 2px;">
                        ${ticket.electionType === 'NACIONAL' ? 'Elección Nacional 2026' : 'Elección Regional 2026'}
                    </div>
                </div>
                <span class="ticket-code">${escapeHtml(ticket.ticketCode)}</span>
            </div>

            <div class="ticket-grid">
                <div>
                    <div class="ticket-field-label">Cédula</div>
                    <div class="ticket-field-value mono">${escapeHtml(ticket.voterId)}</div>
                </div>
                <div>
                    <div class="ticket-field-label">Ciudadano</div>
                    <div class="ticket-field-value">${escapeHtml(ticket.voterName)}</div>
                </div>
                <div>
                    <div class="ticket-field-label">Ciudad / Región</div>
                    <div class="ticket-field-value">${escapeHtml(ticket.city)} (${escapeHtml(ticket.region)})</div>
                </div>
                <div>
                    <div class="ticket-field-label">Candidato Seleccionado</div>
                    <div class="ticket-field-value">${escapeHtml(ticket.candidateName)}</div>
                </div>
                <div>
                    <div class="ticket-field-label">Fecha y Hora</div>
                    <div class="ticket-field-value">${escapeHtml(votedTime)}</div>
                </div>
                <div>
                    <div class="ticket-field-label">Nodo AWS Procesador</div>
                    <div class="ticket-field-value mono">${escapeHtml(ticket.handledByNode)}</div>
                </div>
                <div style="grid-column: 1 / -1;">
                    <div class="ticket-field-label">Sello Criptográfico SHA-256</div>
                    <div class="ticket-field-value mono" style="color: #1e3a8a;">${escapeHtml(ticket.verificationHash)}</div>
                </div>
            </div>

            <div style="display: flex; gap: 0.65rem; flex-wrap: wrap; margin-top: 1.1rem; padding-top: 0.9rem; border-top: 1px dashed #cbd5e1;">
                <button type="button" class="btn btn-outline btn-sm" onclick="window.print()">Imprimir Comprobante</button>
                <button type="button" class="btn btn-primary btn-sm" onclick="selectElection('${otherElection}'); window.scrollTo({top: 0, behavior: 'smooth'});">
                    ${otherLabel} →
                </button>
            </div>
        </div>
    `;
}

async function lookupTickets(e) {
    e.preventDefault();
    const cedula = document.getElementById('lookupCedulaInput').value.trim();
    const container = document.getElementById('lookupResults');
    container.innerHTML = '<div style="font-size: 0.84rem; color: var(--ink-soft);">Consultando registro...</div>';

    try {
        const res = await fetch(`/api/tickets/${encodeURIComponent(cedula)}`);
        const data = await res.json();
        if (!res.ok) {
            container.innerHTML = `<div class="alert-banner alert-error">${escapeHtml(data.message || 'Cédula inválida')}</div>`;
            return;
        }
        if (!data.tickets || data.tickets.length === 0) {
            container.innerHTML = `<div class="alert-banner alert-success">La cédula <strong>${escapeHtml(cedula)}</strong> aún no registra votos emitidos. Está habilitada para votar.</div>`;
            return;
        }
        container.innerHTML = data.tickets.map(t => renderTicketHtml(t, true)).join('');
    } catch (err) {
        container.innerHTML = `<div class="alert-banner alert-error">Error consultando ticket: ${escapeHtml(err.message)}</div>`;
    }
}

async function fetchLiveGlobal() {
    try {
        const res = await fetch('/api/results/global');
        if (res.ok) {
            renderLivePreview(await res.json());
        }
    } catch (_) {}
}

function connectLiveStream() {
    const es = new EventSource('/api/results/global/stream');
    es.addEventListener('global-results', ev => {
        try {
            renderLivePreview(JSON.parse(ev.data));
        } catch (_) {}
    });
    es.onopen = () => {
        const b = document.getElementById('sseBadge');
        b.textContent = 'SSE En Vivo (1s)';
        b.className = 'badge badge-ok';
    };
    es.onerror = () => {
        const b = document.getElementById('sseBadge');
        b.textContent = 'Reconectando...';
        b.className = 'badge badge-warn';
    };
}

function renderLivePreview(data) {
    if (!data) return;
    document.getElementById('heroRealVotes').textContent = Number(data.totalRealVotes || 0).toLocaleString('es-CO');
    document.getElementById('heroTotalVotes').textContent = Number(data.totalVotes || 0).toLocaleString('es-CO');

    renderMiniElection('previewNacBars', catalog?.nationalCandidates || [], data.nationalCandidateVotes || {}, data.nationalRealVotes || {}, data.nationalSimulatedVotes || {}, data.totalNationalVotes || 0);
    renderMiniElection('previewRegBars', catalog?.regionalCandidates || [], data.regionalCandidateVotes || {}, data.regionalRealVotes || {}, data.regionalSimulatedVotes || {}, data.totalRegionalVotes || 0);

    document.getElementById('previewUpdated').textContent =
        'Última transmisión: ' + new Date(data.generatedAt || Date.now()).toLocaleTimeString('es-CO');
}

function renderMiniElection(elId, candidates, totals, reals, sims, grandTotal) {
    const container = document.getElementById(elId);
    if (!candidates.length) {
        container.innerHTML = '<div style="font-size: 0.82rem; color: var(--ink-soft);">Cargando candidatos...</div>';
        return;
    }
    const rows = candidates.map(c => {
        const tot = Number(totals[c.id] || 0);
        const real = Number(reals[c.id] || 0);
        const sim = Number(sims[c.id] || 0);
        const realPct = grandTotal > 0 ? (real / grandTotal) * 100 : 0;
        const simPct = grandTotal > 0 ? (sim / grandTotal) * 100 : 0;
        const totalPct = grandTotal > 0 ? ((tot / grandTotal) * 100).toFixed(1) : '0.0';
        return `
            <div style="margin-bottom: 0.65rem;">
                <div style="display: flex; justify-content: space-between; font-size: 0.82rem; margin-bottom: 0.22rem;">
                    <strong>${escapeHtml(c.name)}</strong>
                    <span>
                        <strong style="color: #047857;">${real} R</strong> ·
                        <span style="color: #4f46e5;">${sim} S</span> ·
                        <strong>${tot.toLocaleString('es-CO')} (${totalPct}%)</strong>
                    </span>
                </div>
                <div class="stacked-bar">
                    <div class="bar-real" style="width: ${realPct}%"></div>
                    <div class="bar-sim" style="width: ${simPct}%"></div>
                </div>
            </div>
        `;
    });
    container.innerHTML = rows.join('');
}

function escapeHtml(str) {
    return String(str ?? '').replace(/[&<>"']/g, s => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[s]));
}

initPortal();
setInterval(fetchLiveGlobal, 4000);
