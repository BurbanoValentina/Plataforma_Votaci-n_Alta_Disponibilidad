let latestOverview = null;
let candidateMap = {};
let searchTimer = null;
const adminMap = new ColombiaElectoralMap('adminColombiaMap', 'ALL');

async function initAdmin() {
    adminMap.init();
    await refreshOverview();
    await Promise.all([
        refreshAuditVotes(),
        refreshVoters(),
        refreshIncidents(),
        loadTelegramConfig(),
        refreshTelegramActivity()
    ]);
    connectAdminStreams();
    setInterval(refreshOverview, 3000);
    setInterval(refreshAuditVotes, 5000);
    setInterval(refreshIncidents, 6000);
    setInterval(refreshTelegramActivity, 2500);
}

function filterAdminMap(region) {
    ['ALL', 'CENTRO', 'NORTE', 'SUR', 'OESTE', 'ESTE'].forEach(r => {
        const btn = document.getElementById('admMapBtn' + r);
        if (btn) btn.className = 'btn btn-sm ' + (r === region ? 'btn-primary' : 'btn-outline');
    });
    adminMap.setRegionFilter(region);
}

async function refreshOverview() {
    try {
        const res = await fetch('/api/admin/overview');
        if (!res.ok) return;
        const data = await res.json();
        latestOverview = data;

        document.getElementById('adminNodeLabel').textContent = `Atendido por: ${data.servedByNode}`;

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
                    <div style="display: flex; align-items: center; gap: 0.55rem;">
                        <img class="candidate-avatar-sm" src="${escapeHtml(c.photoUrl || '')}" alt="${escapeHtml(c.name)}"
                             onerror="this.src='https://ui-avatars.com/api/?name=${encodeURIComponent(c.name)}&background=20B2A6&color=fff'">
                        <div>
                            <strong>${escapeHtml(c.name)}</strong>
                            <div style="font-size: 0.72rem; color: var(--ink-soft);">${escapeHtml(c.party)}</div>
                        </div>
                    </div>
                </td>
                <td><span class="badge badge-real">${r.toLocaleString('es-CO')}</span></td>
                <td><span class="badge badge-brand">${s.toLocaleString('es-CO')}</span></td>
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

    // Only display REGIONAL election candidates (never mix National candidates into Regional table!)
    Object.entries(regions).forEach(([regionKey, data]) => {
        if (filter !== 'ALL' && regionKey !== filter) return;
        const rawVotes = data?.rawVotes || {};
        const realVotes = data?.realVotes || {};
        const simVotes = data?.simulatedVotes || {};
        const weighted = data?.weightedVotes || {};
        const mult = data?.weightMultiplier || 1.0;

        const regionalKeys = Object.keys(rawVotes).filter(id => candidateMap[id]?.electionType === 'REGIONAL');
        if (regionalKeys.length === 0) {
            rows.push(`
                <tr>
                    <td><strong>${regionKey}</strong> <span class="badge badge-neutral">x${Number(mult).toFixed(2)}</span></td>
                    <td colspan="5" style="color: var(--ink-soft);">Esperando votos regionales...</td>
                </tr>
            `);
            return;
        }
        regionalKeys.sort((a, b) => (weighted[b] || 0) - (weighted[a] || 0)).forEach(candId => {
            const prof = candidateMap[candId];
            rows.push(`
                <tr>
                    <td><strong>${regionKey}</strong> <span class="badge badge-neutral">x${Number(mult).toFixed(2)}</span></td>
                    <td>
                        <div style="display: flex; align-items: center; gap: 0.45rem;">
                            <img class="candidate-avatar-sm" style="width: 26px; height: 26px;" src="${escapeHtml(prof?.photoUrl || '')}" alt="${escapeHtml(prof?.name || candId)}"
                                 onerror="this.src='https://ui-avatars.com/api/?name=${encodeURIComponent(prof?.name || candId)}&background=20B2A6&color=fff'">
                            <strong>${escapeHtml(prof ? prof.name : candId)}</strong>
                        </div>
                    </td>
                    <td><span class="badge badge-real">${Number(realVotes[candId] || 0).toLocaleString('es-CO')}</span></td>
                    <td><span class="badge badge-brand">${Number(simVotes[candId] || 0).toLocaleString('es-CO')}</span></td>
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
        grid.innerHTML = '<div style="font-size: 0.82rem; color: var(--ink-soft);">Registrando nodos activos...</div>';
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
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.3rem;">
                    <strong class="mono" style="font-size: 0.76rem;">${escapeHtml(n.node_id)}</strong>
                    <span class="badge ${isCurrent ? 'badge-ok' : 'badge-neutral'}">${escapeHtml(n.availability_zone || 'us-east-1')}</span>
                </div>
                <div style="display: flex; justify-content: space-between; font-size: 0.76rem; color: var(--ink-muted); margin-bottom: 0.3rem;">
                    <span>Req: <strong>${reqs.toLocaleString('es-CO')}</strong></span>
                    <span>Votos: <strong>${votes.toLocaleString('es-CO')}</strong></span>
                    <span><strong>${share}%</strong></span>
                </div>
                <div class="stacked-bar" style="height: 8px;">
                    <div class="bar-sim" style="width: ${share}%"></div>
                </div>
            </div>
        `;
    }).join('');
}

async function pingLoadBalancer() {
    const log = document.getElementById('lbPingLog');
    log.innerHTML = 'Enviando 6 pruebas HTTP al AWS Application Load Balancer...';
    const items = [];
    for (let i = 1; i <= 6; i++) {
        const t0 = performance.now();
        const res = await fetch('/api/health?probe=' + Date.now() + '-' + i);
        const ms = Math.round(performance.now() - t0);
        const data = await res.json();
        const isNode1 = String(data.nodeId || '').includes('node-1');
        items.push(`<span class="badge ${isNode1 ? 'badge-brand' : 'badge-real'} mono">#${i} ${escapeHtml(data.nodeId)} · ${ms}ms</span>`);
    }
    log.innerHTML = `<div style="margin-bottom: 0.3rem;"><strong>Resultado en vivo del balanceo Round-Robin (AWS ALB):</strong></div><div style="display:flex; flex-wrap:wrap; gap:0.35rem;">${items.join('')}</div>`;
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
    document.getElementById('simFeedback').textContent = `Ráfaga de ${data.count} votos simulados despachada desde ${data.servedByNode}.`;
    setTimeout(() => { refreshOverview(); refreshAuditVotes(); adminMap.refresh(); }, 1200);
}

async function triggerBotAttack() {
    await fetch('/api/admin/simulator', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ triggerBotAttack: true })
    });
    document.getElementById('simFeedback').textContent = 'Ataque Bot (18 votos rápidos desde 1 IP) despachado — observe la Tabla 5.';
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
                    <td><span class="badge ${isReal ? 'badge-real' : 'badge-brand'}">${isReal ? 'REAL' : 'SIMULADO'}</span></td>
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
                    <div style="background: #fef2f2; border: 1px solid #fecaca; border-radius: 10px; padding: 0.7rem 0.85rem;">
                        <div style="font-size: 0.76rem; font-weight: 800; color: #991b1b; margin-bottom: 0.4rem;">
                            ENTIDADES BLOQUEADAS ACTIVAS (${blocked.length})
                        </div>
                        <div style="display: flex; flex-direction: column; gap: 0.35rem;">
                            ${blocked.map(b => `
                                <div style="display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; font-size: 0.78rem;">
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
                    <td style="font-size: 0.78rem;">${escapeHtml(inc.reason)}</td>
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

// ==================== Telegram Control Center ====================

function toggleTgConfigPanel() {
    document.getElementById('tgConfigPanel').classList.toggle('hidden');
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
            document.getElementById('tgBotToken').placeholder = 'Token guardado (escribe uno nuevo solo para cambiarlo)';
        }
        const badge = document.getElementById('tgStatusBadge');
        if (cfg.enabled && cfg.tokenConfigured) {
            badge.textContent = 'ACTIVO (POLLING)';
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
        document.getElementById('tgFeedback').textContent = '✔ Bot de Telegram guardado y sincronizado en todos los nodos AWS. Presiona "Enviar Menú / Probar".';
        loadTelegramConfig();
    }
}

async function testTelegram() {
    const fb = document.getElementById('tgFeedback');
    fb.textContent = 'Enviando menú interactivo de prueba a Telegram...';
    const res = await fetch('/api/telegram/test', { method: 'POST' });
    const data = await res.json();
    fb.textContent = data.ok
        ? '✔ Menú interactivo enviado a Telegram. Ya puedes pasarle tu Telegram al profesor.'
        : '✖ ' + (data.error || 'Abre "Configurar API" e ingresa tu Bot Token y Chat ID.');
    refreshTelegramActivity();
}

async function sendTelegramRunoff() {
    const fb = document.getElementById('tgFeedback');
    fb.textContent = 'Enviando Informe de Segunda Vuelta a Telegram...';
    const res = await fetch('/api/telegram/send-runoff', { method: 'POST' });
    const data = await res.json();
    fb.textContent = data.ok ? '✔ Informe de Segunda Vuelta enviado a Telegram.' : '✖ ' + (data.error || 'Configura primero tu Bot Token y Chat ID.');
    refreshTelegramActivity();
}

async function sendTelegramNodes() {
    const fb = document.getElementById('tgFeedback');
    fb.textContent = 'Enviando Informe de Distribución de Carga AWS a Telegram...';
    const res = await fetch('/api/telegram/send-nodes', { method: 'POST' });
    const data = await res.json();
    fb.textContent = data.ok ? '✔ Informe de Nodos AWS enviado a Telegram.' : '✖ ' + (data.error || 'Configura primero tu Bot Token y Chat ID.');
    refreshTelegramActivity();
}

async function sendTelegramSummary() {
    const fb = document.getElementById('tgFeedback');
    fb.textContent = 'Enviando Resumen Electoral Completo a Telegram...';
    const res = await fetch('/api/telegram/send-summary', { method: 'POST' });
    const data = await res.json();
    fb.textContent = data.ok ? '✔ Resumen Electoral enviado a Telegram.' : '✖ ' + (data.error || 'Configura primero tu Bot Token y Chat ID.');
    refreshTelegramActivity();
}

async function refreshTelegramActivity() {
    try {
        const res = await fetch('/api/telegram/activity?limit=12');
        if (!res.ok) return;
        const data = await res.json();
        const feed = document.getElementById('tgActivityFeed');
        const events = data.events || [];
        if (!events.length) {
            feed.innerHTML = '<div style="font-size: 0.77rem; color: var(--ink-soft);">Aún no hay interacciones desde Telegram. Cuando el profesor pida un informe, lance una simulación o cambie el estado electoral en Telegram, aparecerá aquí en vivo.</div>';
            return;
        }
        feed.innerHTML = events.map(ev => {
            const t = ev.created_at ? new Date(ev.created_at).toLocaleTimeString('es-CO') : '';
            return `
                <div class="tg-feed-item">
                    <div style="display: flex; justify-content: space-between; gap: 0.4rem; margin-bottom: 2px;">
                        <span class="badge badge-brand">${escapeHtml(ev.action_type)}</span>
                        <span style="font-size: 0.7rem; color: var(--ink-soft);">${escapeHtml(t)} · ${escapeHtml(ev.handled_by_node)}</span>
                    </div>
                    <div><strong>${escapeHtml(ev.message)}</strong></div>
                </div>
            `;
        }).join('');
    } catch (_) {}
}

function escapeHtml(str) {
    return String(str ?? '').replace(/[&<>"']/g, s => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[s]));
}

initAdmin();
