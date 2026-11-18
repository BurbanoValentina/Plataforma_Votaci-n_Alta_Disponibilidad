let latestOverview = null;
let candidateMap = {};
let searchTimer = null;

async function initAdmin() {
    await refreshOverview();
    await Promise.all([refreshAuditVotes(), refreshVoters(), refreshIncidents(), loadTelegramConfig()]);
    const wh = document.getElementById('tgWebhookUrl');
    if (wh) wh.textContent = window.location.origin + '/api/telegram/webhook';
    connectAdminStreams();
    setInterval(refreshOverview, 3000);
    setInterval(refreshAuditVotes, 5000);
    setInterval(refreshIncidents, 6000);
}

async function refreshOverview() {
    try {
        const res = await fetch('/api/admin/overview');
        if (!res.ok) return;
        const data = await res.json();
        latestOverview = data;

        document.getElementById('adminNodeLabel').textContent = `Atendido por: ${data.servedByNode} (${data.availabilityZone})`;

        if (Array.isArray(data.candidates)) {
            candidateMap = {};
            data.candidates.forEach(c => { candidateMap[c.id] = c; });
        }

        renderControlState(data.control || {});
        renderGlobalKpisAndNational(data.globalResults || {}, data.dbCounters || {}, data.ktableSize || 0);
        renderRegionalTable();
        renderNodes(data.nodes || [], data.servedByNode);
    } catch (_) {}
}

function connectAdminStreams() {
    const esGlobal = new EventSource('/api/results/global/stream');
    esGlobal.addEventListener('global-results', ev => {
        try {
            const globalData = JSON.parse(ev.data);
            if (latestOverview) {
                latestOverview.globalResults = globalData;
                renderGlobalKpisAndNational(globalData, latestOverview.dbCounters || {}, latestOverview.ktableSize || 0);
            }
        } catch (_) {}
    });

    const esReg = new EventSource('/api/results/regional/stream');
    esReg.addEventListener('regional-update', ev => {
        try {
            const upd = JSON.parse(ev.data);
            if (latestOverview && latestOverview.regionalResults && upd.region) {
                latestOverview.regionalResults[upd.region] = upd;
                renderRegionalTable();
            }
        } catch (_) {}
    });
}

function renderControlState(ctrl) {
    const state = ctrl.lifecycle_state || 'HABILITADO';
    const badge = document.getElementById('ctrlStateBadge');
    badge.textContent = state;
    badge.className = 'badge ' + (state === 'HABILITADO' ? 'badge-ok' : state === 'EN_AUDITORIA' ? 'badge-warn' : 'badge-danger');

    const strict = Boolean(ctrl.strict_census_mode);
    const strictBtn = document.getElementById('strictModeBtn');
    strictBtn.textContent = strict ? 'Activado (Estricto)' : 'Desactivado (Abierto)';
    strictBtn.className = 'btn btn-sm ' + (strict ? 'btn-warning' : 'btn-outline');

    const simOn = Boolean(ctrl.simulator_enabled);
    const simBadge = document.getElementById('simStatusBadge');
    simBadge.textContent = simOn ? `ACTIVO (${ctrl.simulator_batch_size || 2} votos / ${ctrl.simulator_rate_ms || 1800}ms)` : 'PAUSADO';
    simBadge.className = 'badge ' + (simOn ? 'badge-sim' : 'badge-neutral');

    const toggleBtn = document.getElementById('simToggleBtn');
    toggleBtn.textContent = simOn ? 'Pausar Simulador' : 'Reanudar Simulador';
    toggleBtn.className = 'btn btn-sm ' + (simOn ? 'btn-warning' : 'btn-success');
}

function renderGlobalKpisAndNational(g, dbCounters, ktableSize) {
    const total = Number(g.totalVotes || 0);
    const real = Number(g.totalRealVotes || 0);
    const sim = Number(g.totalSimulatedVotes || 0);
    const nac = Number(g.totalNationalVotes || 0);
    const reg = Number(g.totalRegionalVotes || 0);

    document.getElementById('kpiTotal').textContent = total.toLocaleString('es-CO');
    document.getElementById('kpiSplitElections').textContent = `Nacional: ${nac.toLocaleString('es-CO')} · Regional: ${reg.toLocaleString('es-CO')}`;

    document.getElementById('kpiReal').textContent = real.toLocaleString('es-CO');
    document.getElementById('kpiRealPct').textContent = `${total > 0 ? ((real / total) * 100).toFixed(1) : '0.0'}% del total emitido`;

    document.getElementById('kpiSim').textContent = sim.toLocaleString('es-CO');
    document.getElementById('kpiSimPct').textContent = `${total > 0 ? ((sim / total) * 100).toFixed(1) : '0.0'}% del total emitido`;

    document.getElementById('kpiVoters').textContent = Number(ktableSize || dbCounters.eligible_voters || 0).toLocaleString('es-CO');
    document.getElementById('kpiTickets').textContent = `Tickets ciudadanos únicos: ${Number(dbCounters.total_tickets || 0).toLocaleString('es-CO')}`;
    document.getElementById('kpiIncidents').textContent = Number(dbCounters.total_incidents || 0).toLocaleString('es-CO');

    document.getElementById('nacTotalBadge').textContent = `${nac.toLocaleString('es-CO')} votos`;

    const nacTotals = g.nationalCandidateVotes || {};
    const nacReals = g.nationalRealVotes || {};
    const nacSims = g.nationalSimulatedVotes || {};

    const nacCandidates = (latestOverview?.candidates || []).filter(c => c.electionType === 'NACIONAL');
    const tbody = document.getElementById('nationalTableBody');

    const sorted = [...nacCandidates].sort((a, b) => (nacTotals[b.id] || 0) - (nacTotals[a.id] || 0));
    tbody.innerHTML = sorted.map(c => {
        const t = Number(nacTotals[c.id] || 0);
        const r = Number(nacReals[c.id] || 0);
        const s = Number(nacSims[c.id] || 0);
        const pct = nac > 0 ? ((t / nac) * 100).toFixed(1) : '0.0';
        const rPct = nac > 0 ? (r / nac) * 100 : 0;
        const sPct = nac > 0 ? (s / nac) * 100 : 0;
        return `
            <tr>
                <td><strong>#${c.ballotNumber}</strong></td>
                <td>
                    <strong>${escapeHtml(c.name)}</strong>
                    <div style="font-size: 0.75rem; color: var(--ink-soft);">${escapeHtml(c.party)}</div>
                </td>
                <td><span class="badge badge-real">${r.toLocaleString('es-CO')}</span></td>
                <td><span class="badge badge-sim">${s.toLocaleString('es-CO')}</span></td>
                <td><strong>${t.toLocaleString('es-CO')}</strong></td>
                <td><strong>${pct}%</strong></td>
                <td>
                    <div class="stacked-bar">
                        <div class="bar-real" style="width: ${rPct}%"></div>
                        <div class="bar-sim" style="width: ${sPct}%"></div>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

function renderRegionalTable() {
    if (!latestOverview || !latestOverview.regionalResults) return;
    const filter = document.getElementById('regionFilterSelect').value;
    const regions = latestOverview.regionalResults;
    const tbody = document.getElementById('regionalTableBody');
    const rows = [];

    Object.entries(regions).forEach(([regionKey, data]) => {
        if (filter !== 'ALL' && regionKey !== filter) return;
        const rawVotes = data?.rawVotes || {};
        const realVotes = data?.realVotes || {};
        const simVotes = data?.simulatedVotes || {};
        const weighted = data?.weightedVotes || {};
        const mult = data?.weightMultiplier || 1.0;

        const entries = Object.keys(rawVotes);
        if (entries.length === 0) {
            rows.push(`
                <tr>
                    <td><strong>${regionKey}</strong> <span class="badge badge-neutral">x${Number(mult).toFixed(2)}</span></td>
                    <td colspan="5" style="color: var(--ink-soft);">Esperando votos regionales...</td>
                </tr>
            `);
            return;
        }
        entries.sort((a, b) => (weighted[b] || 0) - (weighted[a] || 0)).forEach(candId => {
            const prof = candidateMap[candId];
            const name = prof ? prof.name : candId;
            rows.push(`
                <tr>
                    <td><strong>${regionKey}</strong> <span class="badge badge-neutral">x${Number(mult).toFixed(2)}</span></td>
                    <td><strong>${escapeHtml(name)}</strong></td>
                    <td><span class="badge badge-real">${Number(realVotes[candId] || 0).toLocaleString('es-CO')}</span></td>
                    <td><span class="badge badge-sim">${Number(simVotes[candId] || 0).toLocaleString('es-CO')}</span></td>
                    <td>${Number(rawVotes[candId] || 0).toLocaleString('es-CO')}</td>
                    <td><strong style="color: #6b21a8;">${Number(weighted[candId] || 0).toFixed(2)} pts</strong></td>
                </tr>
            `);
        });
    });

    tbody.innerHTML = rows.join('');
}

function renderNodes(nodes, currentNode) {
    const grid = document.getElementById('nodesGrid');
    if (!nodes.length) {
        grid.innerHTML = '<div style="font-size: 0.84rem; color: var(--ink-soft);">Registrando nodos activos...</div>';
        return;
    }
    const totalReqs = nodes.reduce((acc, n) => acc + Number(n.requests_handled || 0) + Number(n.votes_processed || 0), 0) || 1;
    grid.innerHTML = nodes.map(n => {
        const reqs = Number(n.requests_handled || 0);
        const votes = Number(n.votes_processed || 0);
        const share = (((reqs + votes) / totalReqs) * 100).toFixed(1);
        const isCurrent = n.node_id === currentNode;
        return `
            <div class="node-card ${isCurrent ? 'active-node' : ''}">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.35rem;">
                    <strong class="mono">${escapeHtml(n.node_id)}</strong>
                    <span class="badge ${isCurrent ? 'badge-ok' : 'badge-neutral'}">${escapeHtml(n.availability_zone || 'us-east-1')}</span>
                </div>
                <div style="display: flex; justify-content: space-between; font-size: 0.8rem; color: var(--ink-muted); margin-bottom: 0.35rem;">
                    <span>Peticiones: <strong>${reqs.toLocaleString('es-CO')}</strong></span>
                    <span>Votos: <strong>${votes.toLocaleString('es-CO')}</strong></span>
                    <span>Carga: <strong>${share}%</strong></span>
                </div>
                <div class="stacked-bar" style="height: 8px;">
                    <div class="bar-real" style="width: ${share}%"></div>
                </div>
            </div>
        `;
    }).join('');
}

async function pingLoadBalancer() {
    const log = document.getElementById('lbPingLog');
    const hits = [];
    for (let i = 0; i < 6; i++) {
        const res = await fetch('/api/health?t=' + Date.now() + '-' + i);
        const data = await res.json();
        hits.push(data.nodeId);
    }
    log.innerHTML = `<strong>6 pruebas consecutivas vía AWS ALB:</strong> ${hits.map(h => `<span class="badge badge-neutral mono">${escapeHtml(h)}</span>`).join(' ')}`;
    refreshOverview();
}

async function setLifecycle(nextState) {
    await fetch('/api/admin/lifecycle', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ lifecycleState: nextState })
    });
    refreshOverview();
}

async function toggleStrictMode() {
    const current = Boolean(latestOverview?.control?.strict_census_mode);
    await fetch('/api/admin/lifecycle', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ strictCensusMode: !current })
    });
    refreshOverview();
}

async function toggleSimulator() {
    const current = Boolean(latestOverview?.control?.simulator_enabled);
    await fetch('/api/admin/simulator', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ simulatorEnabled: !current })
    });
    refreshOverview();
}

async function setSimSpeed(rateMs, batchSize) {
    await fetch('/api/admin/simulator', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ simulatorEnabled: true, simulatorRateMs: rateMs, simulatorBatchSize: batchSize })
    });
    document.getElementById('simFeedback').textContent = `Velocidad ajustada: ${batchSize} votos cada ${rateMs}ms.`;
    refreshOverview();
}

async function triggerBurst() {
    const res = await fetch('/api/admin/simulator/burst?count=25', { method: 'POST' });
    const data = await res.json();
    document.getElementById('simFeedback').textContent = `⚡ Ráfaga de ${data.count} votos simulados despachada desde ${data.servedByNode}.`;
    setTimeout(() => { refreshOverview(); refreshAuditVotes(); }, 1200);
}

async function triggerBotAttack() {
    await fetch('/api/admin/simulator', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ triggerBotAttack: true })
    });
    document.getElementById('simFeedback').textContent = '🛡️ Ataque Bot (18 votos rápidos desde 1 IP) despachado — observe la Tabla 5 de incidentes Anti-Fraude.';
    setTimeout(() => { refreshIncidents(); refreshOverview(); }, 2500);
}

function debounceAuditSearch() {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(refreshAuditVotes, 300);
}

async function refreshAuditVotes() {
    const election = document.getElementById('filterElection').value;
    const source = document.getElementById('filterSource').value;
    const region = document.getElementById('filterRegion').value;
    const q = document.getElementById('filterSearch').value.trim();

    const params = new URLSearchParams({ electionType: election, voteSource: source, region, q, limit: '80' });
    try {
        const res = await fetch('/api/admin/votes?' + params.toString());
        if (!res.ok) return;
        const data = await res.json();
        const tbody = document.getElementById('auditVotesTableBody');
        if (!data.votes || !data.votes.length) {
            tbody.innerHTML = '<tr><td colspan="9" style="text-align: center; color: var(--ink-soft);">Sin registros para los filtros seleccionados.</td></tr>';
            return;
        }
        tbody.innerHTML = data.votes.map(v => {
            const isReal = v.vote_source === 'REAL';
            const isNac = v.election_type === 'NACIONAL';
            const dt = v.voted_at ? new Date(v.voted_at).toLocaleTimeString('es-CO') : '-';
            return `
                <tr>
                    <td class="mono">${escapeHtml(v.ticket_code)}</td>
                    <td class="mono"><strong>${escapeHtml(v.voter_id)}</strong></td>
                    <td><span class="badge ${isNac ? 'badge-nac' : 'badge-reg'}">${escapeHtml(v.election_type)}</span></td>
                    <td><strong>${escapeHtml(v.candidate_name)}</strong></td>
                    <td>${escapeHtml(v.city)} <span style="color: var(--ink-soft);">(${escapeHtml(v.region)})</span></td>
                    <td><span class="badge ${isReal ? 'badge-real' : 'badge-sim'}">${isReal ? 'REAL' : 'SIMULADO'}</span></td>
                    <td class="mono">${escapeHtml(v.handled_by_node)}</td>
                    <td class="mono">${escapeHtml(v.ip_address)}</td>
                    <td>${escapeHtml(dt)}</td>
                </tr>
            `;
        }).join('');
    } catch (_) {}
}

async function refreshVoters() {
    try {
        const res = await fetch('/api/admin/voters');
        if (!res.ok) return;
        const data = await res.json();
        const tbody = document.getElementById('votersTableBody');
        tbody.innerHTML = (data.voters || []).map(v => {
            const flags = [];
            if (v.voted_nacional) flags.push('<span class="badge badge-nac">NAC</span>');
            if (v.voted_regional) flags.push('<span class="badge badge-reg">REG</span>');
            return `
                <tr>
                    <td class="mono"><strong>${escapeHtml(v.voter_id)}</strong></td>
                    <td>${escapeHtml(v.full_name)}</td>
                    <td>${escapeHtml(v.city)} (${escapeHtml(v.region)})</td>
                    <td>${flags.length ? flags.join(' ') : '<span style="color: var(--ink-soft);">Pendiente</span>'}</td>
                    <td><span class="badge ${v.eligible ? 'badge-ok' : 'badge-danger'}">${v.eligible ? 'Habilitado' : 'Revocado'}</span></td>
                    <td>
                        <button class="btn btn-outline btn-sm" onclick="toggleVoter('${escapeHtml(v.voter_id)}', ${!v.eligible})">
                            ${v.eligible ? 'Revocar' : 'Habilitar'}
                        </button>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (_) {}
}

async function addNewVoter(e) {
    e.preventDefault();
    const voterId = document.getElementById('newVoterCedula').value.trim();
    const fullName = document.getElementById('newVoterName').value.trim();
    const city = document.getElementById('newVoterCity').value;
    await fetch('/api/admin/voters', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ voterId, fullName, city })
    });
    document.getElementById('newVoterCedula').value = '';
    document.getElementById('newVoterName').value = '';
    refreshVoters();
    refreshOverview();
}

async function toggleVoter(voterId, nextEligible) {
    await fetch(`/api/admin/voters/${encodeURIComponent(voterId)}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ eligible: nextEligible })
    });
    refreshVoters();
}

async function refreshIncidents() {
    try {
        const res = await fetch('/api/admin/incidents?limit=40');
        if (!res.ok) return;
        const data = await res.json();

        const blockedBox = document.getElementById('blockedEntitiesBox');
        const blocked = data.blockedEntities || [];
        if (blockedBox) {
            if (!blocked.length) {
                blockedBox.innerHTML = '';
            } else {
                blockedBox.innerHTML = `
                    <div style="background: #fef2f2; border: 1px solid #fecaca; border-radius: 10px; padding: 0.75rem 0.9rem;">
                        <div style="font-size: 0.78rem; font-weight: 800; color: #991b1b; margin-bottom: 0.45rem;">
                            ENTIDADES BLOQUEADAS ACTIVAS (${blocked.length})
                        </div>
                        <div style="display: flex; flex-direction: column; gap: 0.4rem;">
                            ${blocked.map(b => `
                                <div style="display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; font-size: 0.8rem;">
                                    <span><strong class="mono">${escapeHtml(b.entity_key)}</strong> — ${escapeHtml(b.reason)}</span>
                                    <button class="btn btn-outline btn-sm" onclick="unblockEntity('${escapeHtml(b.entity_key)}')">Desbloquear</button>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                `;
            }
        }

        const tbody = document.getElementById('incidentsTableBody');
        if (!data.incidents || !data.incidents.length) {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--ink-soft);">Sin incidentes detectados.</td></tr>';
            return;
        }
        tbody.innerHTML = data.incidents.map(inc => {
            const isBlocked = inc.risk_level === 'BLOQUEADA';
            const dt = inc.detected_at ? new Date(inc.detected_at).toLocaleTimeString('es-CO') : '-';
            return `
                <tr>
                    <td class="mono"><strong>${escapeHtml(inc.ip_address)}</strong></td>
                    <td><span class="badge ${isBlocked ? 'badge-danger' : 'badge-warn'}">${escapeHtml(inc.risk_level)}</span></td>
                    <td><strong>${inc.vote_count}</strong></td>
                    <td style="font-size: 0.8rem;">${escapeHtml(inc.reason)}</td>
                    <td>${escapeHtml(dt)}</td>
                </tr>
            `;
        }).join('');
    } catch (_) {}
}

async function unblockEntity(entityKey) {
    await fetch('/api/admin/blocked/' + encodeURIComponent(entityKey), { method: 'DELETE' });
    refreshIncidents();
    refreshOverview();
}

async function loadTelegramConfig() {
    try {
        const res = await fetch('/api/telegram/config');
        if (!res.ok) return;
        const cfg = await res.json();
        document.getElementById('tgChatId').value = cfg.chatId || '';
        document.getElementById('tgEnabled').checked = Boolean(cfg.enabled);
        document.getElementById('tgNotifyVotes').checked = cfg.notifyVotes !== false;
        document.getElementById('tgNotifyFraud').checked = cfg.notifyFraud !== false;
        if (cfg.tokenConfigured) {
            document.getElementById('tgBotToken').placeholder = 'Token guardado (escribe uno nuevo solo para reemplazar)';
        }
        const badge = document.getElementById('tgStatusBadge');
        if (cfg.enabled && cfg.tokenConfigured) {
            badge.textContent = 'CONECTADO Y ACTIVO';
            badge.className = 'badge badge-ok';
        } else if (cfg.tokenConfigured) {
            badge.textContent = 'CONFIGURADO (PAUSADO)';
            badge.className = 'badge badge-warn';
        } else {
            badge.textContent = 'LISTO PARA CONECTAR';
            badge.className = 'badge badge-neutral';
        }
    } catch (_) {}
}

async function saveTelegramConfig(e) {
    e.preventDefault();
    const token = document.getElementById('tgBotToken').value.trim();
    const chatId = document.getElementById('tgChatId').value.trim();
    const enabled = document.getElementById('tgEnabled').checked;
    const notifyVotes = document.getElementById('tgNotifyVotes').checked;
    const notifyFraud = document.getElementById('tgNotifyFraud').checked;

    const payload = { enabled, chatId, notifyVotes, notifyFraud };
    if (token) payload.botToken = token;

    const res = await fetch('/api/telegram/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    if (res.ok) {
        document.getElementById('tgBotToken').value = '';
        document.getElementById('tgFeedback').textContent = '✔ Configuración de Telegram guardada en PostgreSQL (sincronizada en todos los nodos AWS).';
        loadTelegramConfig();
    }
}

async function testTelegram() {
    const fb = document.getElementById('tgFeedback');
    fb.textContent = 'Enviando mensaje de prueba a Telegram...';
    const res = await fetch('/api/telegram/test', { method: 'POST' });
    const data = await res.json();
    fb.textContent = data.ok
        ? '✔ Mensaje de prueba enviado exitosamente a tu Telegram.'
        : '✖ Error Telegram: ' + (data.error || data.response || 'Revisa Bot Token y Chat ID.');
}

async function sendTelegramSummary() {
    const fb = document.getElementById('tgFeedback');
    fb.textContent = 'Enviando resumen electoral y predicción de segunda vuelta...';
    const res = await fetch('/api/telegram/send-summary', { method: 'POST' });
    const data = await res.json();
    fb.textContent = data.ok
        ? '✔ Resumen electoral en vivo enviado a tu Telegram.'
        : '✖ Error Telegram: ' + (data.error || data.response || 'Configura primero Bot Token y Chat ID.');
}

function escapeHtml(str) {
    return String(str ?? '').replace(/[&<>"']/g, s => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[s]));
}

initAdmin();
