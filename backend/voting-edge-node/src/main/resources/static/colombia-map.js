/**
 * Reusable Live Colombia Electoral Map (Leaflet.js).
 * Highlights cities with REAL citizen votes in glowing Orange/Coral (#f97316) with pulse animation,
 * vs SIMULATED-only cities in Teal (#20B2A6). Supports region zoom filtering.
 */
class ColombiaElectoralMap {
    constructor(elementId, electionType = 'ALL') {
        this.elementId = elementId;
        this.electionType = electionType;
        this.regionFilter = 'ALL';
        this.map = null;
        this.markersLayer = null;
        this.points = [];

        this.regionCenters = {
            ALL: { lat: 4.9, lng: -74.0, zoom: 5.5 },
            CENTRO: { lat: 5.6, lng: -74.4, zoom: 7 },
            NORTE: { lat: 10.1, lng: -75.1, zoom: 7 },
            SUR: { lat: 2.0, lng: -76.3, zoom: 7 },
            ESTE: { lat: 6.0, lng: -72.2, zoom: 7 },
            OESTE: { lat: 4.5, lng: -76.2, zoom: 7 }
        };
    }

    init() {
        const el = document.getElementById(this.elementId);
        if (!el || typeof L === 'undefined') return;

        this.map = L.map(this.elementId, {
            center: [4.9, -74.0],
            zoom: 5.5,
            zoomControl: true,
            scrollWheelZoom: false
        });

        L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
            attribution: '&copy; OpenStreetMap &copy; CARTO Voyager',
            maxZoom: 16
        }).addTo(this.map);

        this.markersLayer = L.layerGroup().addTo(this.map);
        this.refresh();
        setInterval(() => this.refresh(), 3500);
    }

    setRegionFilter(region) {
        this.regionFilter = region || 'ALL';
        const view = this.regionCenters[this.regionFilter] || this.regionCenters.ALL;
        if (this.map) {
            this.map.flyTo([view.lat, view.lng], view.zoom, { duration: 0.7 });
        }
        this.renderPoints();
    }

    setElectionType(type) {
        this.electionType = type || 'ALL';
        this.refresh();
    }

    async refresh() {
        try {
            const res = await fetch('/api/map/activity?electionType=' + encodeURIComponent(this.electionType));
            if (!res.ok) return;
            const data = await res.json();
            this.points = data.points || [];
            this.renderPoints();
        } catch (_) {}
    }

    renderPoints() {
        if (!this.markersLayer) return;
        this.markersLayer.clearLayers();

        this.points.forEach(pt => {
            if (this.regionFilter !== 'ALL' && pt.region !== this.regionFilter) return;

            const hasReal = Number(pt.realVotes || 0) > 0;
            const total = Number(pt.totalVotes || 0);
            const size = hasReal
                ? Math.min(30, 18 + Math.floor(Math.log2(pt.realVotes + 1) * 4))
                : Math.min(24, 12 + Math.floor(Math.log2(total + 1) * 1.4));

            const icon = L.divIcon({
                className: hasReal ? 'real-vote-marker' : 'sim-vote-marker',
                iconSize: [size, size],
                iconAnchor: [size / 2, size / 2]
            });

            const popupHtml = `
                <div style="font-family: 'Plus Jakarta Sans', sans-serif; min-width: 175px;">
                    <div style="font-weight: 800; font-size: 0.95rem; color: #0c1f24;">${pt.city}</div>
                    <div style="font-size: 0.75rem; color: #158a80; font-weight: 700; margin-bottom: 6px;">Región ${pt.region}</div>
                    <div style="font-size: 0.82rem; display: flex; justify-content: space-between;">
                        <span>Votos Reales:</span>
                        <strong style="color: #c2410c;">${Number(pt.realVotes || 0).toLocaleString('es-CO')}</strong>
                    </div>
                    <div style="font-size: 0.82rem; display: flex; justify-content: space-between;">
                        <span>Votos Simulados:</span>
                        <strong style="color: #158a80;">${Number(pt.simulatedVotes || 0).toLocaleString('es-CO')}</strong>
                    </div>
                    <div style="font-size: 0.82rem; display: flex; justify-content: space-between; margin-top: 4px; padding-top: 4px; border-top: 1px solid #e2e8f0;">
                        <span>Total zona:</span>
                        <strong>${total.toLocaleString('es-CO')}</strong>
                    </div>
                    ${hasReal ? '<div style="margin-top:6px; font-size:0.72rem; background:#fff7ed; color:#c2410c; padding:3px 6px; border-radius:6px; font-weight:700;">● Zona con votación ciudadana REAL activa</div>' : ''}
                </div>
            `;

            L.marker([pt.lat, pt.lng], { icon })
                .bindPopup(popupHtml)
                .bindTooltip(`${pt.city}: ${pt.realVotes} Reales / ${pt.simulatedVotes} Sim.`, { direction: 'top', offset: [0, -8] })
                .addTo(this.markersLayer);
        });
    }
}
