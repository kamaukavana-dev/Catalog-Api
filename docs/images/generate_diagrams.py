#!/usr/bin/env python3
"""
Generate the three README diagrams as real SVG files from the actual source tree.

Run:  python3 docs/images/generate_diagrams.py   (from the repo root)

Outputs (docs/images/):
  1. module-dependencies.svg  -- inter-context coupling, PARSED from real `import
     com.catalog.*` statements under src/main/java. Re-run after refactors to keep honest.
  2. reservation-write-path.svg -- the idempotent inventory-reservation write path,
     encoded from the real filter @Order values, IdempotencyFilter (atomic SET-NX claim)
     and InventoryService.reserveStock (@Transactional/@Retryable).
  3. erd-core.svg -- core catalog ER model, encoded from the @Entity classes and the
     Flyway migrations (V1..V17). Field lists are trimmed to keys + relationships.

No external tools required (no graphviz/mermaid): the SVG is emitted directly so it
renders offline and diffs as text.
"""
import os
import re
import math

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "src", "main", "java", "com", "catalog")
OUT = os.path.dirname(os.path.abspath(__file__))

MODULES = ["attribute", "brand", "category", "product", "variant",
           "inventory", "warehouse", "media", "order", "common"]


# --------------------------------------------------------------------------- #
# tiny SVG helpers
# --------------------------------------------------------------------------- #
def esc(s):
    return (str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def svg_header(w, h, title):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
        f'viewBox="0 0 {w} {h}" font-family="Segoe UI, Helvetica, Arial, sans-serif">\n'
        f'<title>{esc(title)}</title>\n'
        f'<rect x="0" y="0" width="{w}" height="{h}" fill="#ffffff"/>\n'
        '<defs>\n'
        '  <marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="3" '
        'orient="auto" markerUnits="strokeWidth">\n'
        '    <path d="M0,0 L9,3 L0,6 z" fill="#334155"/>\n'
        '  </marker>\n'
        '  <marker id="arrow-blue" markerWidth="10" markerHeight="10" refX="9" refY="3" '
        'orient="auto" markerUnits="strokeWidth">\n'
        '    <path d="M0,0 L9,3 L0,6 z" fill="#1d4ed8"/>\n'
        '  </marker>\n'
        '</defs>\n'
    )


def box(x, y, w, h, fill, stroke, rx=8, sw=1.5):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>\n')


def text(x, y, s, size=13, fill="#0f172a", anchor="middle", weight="normal"):
    return (f'<text x="{x}" y="{y}" font-size="{size}" fill="{fill}" '
            f'text-anchor="{anchor}" font-weight="{weight}">{esc(s)}</text>\n')


def line(x1, y1, x2, y2, stroke="#334155", sw=1.5, marker="arrow", dash=None):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    m = f' marker-end="url(#{marker})"' if marker else ""
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{stroke}" '
            f'stroke-width="{sw}"{d}{m}/>\n')


# --------------------------------------------------------------------------- #
# 1. module dependency graph  (parsed from imports)
# --------------------------------------------------------------------------- #
def parse_edges():
    pat = re.compile(r'import\s+com\.catalog\.([a-z]+)')
    edges = {}
    for src in MODULES:
        d = os.path.join(SRC, src)
        if not os.path.isdir(d):
            continue
        for dirpath, _, files in os.walk(d):
            for f in files:
                if not f.endswith(".java"):
                    continue
                with open(os.path.join(dirpath, f), encoding="utf-8") as fh:
                    for m in pat.finditer(fh.read()):
                        dst = m.group(1)
                        if dst in MODULES and dst != src:
                            edges[(src, dst)] = edges.get((src, dst), 0) + 1
    return edges


def module_diagram():
    edges = parse_edges()
    W, H = 940, 720
    cx, cy = W / 2, 360
    R = 250
    domain = [m for m in MODULES if m != "common"]
    pos = {}
    for i, m in enumerate(domain):
        ang = -math.pi / 2 + 2 * math.pi * i / len(domain)
        pos[m] = (cx + R * math.cos(ang), cy + R * math.sin(ang))
    pos["common"] = (cx, cy)

    s = svg_header(W, H, "Catalog API - module (bounded-context) dependencies")
    s += text(W / 2, 34, "Module / bounded-context dependencies", 20, "#0f172a", weight="bold")
    s += text(W / 2, 56, "Edges parsed from actual `import com.catalog.*` statements  (label = # of imports)",
              12, "#64748b")

    bw, bh = 118, 40
    # edges first (under nodes)
    for (a, b), wgt in sorted(edges.items()):
        ax, ay = pos[a]
        bx, by = pos[b]
        dx, dy = bx - ax, by - ay
        dist = math.hypot(dx, dy) or 1
        ux, uy = dx / dist, dy / dist
        # trim to box edges
        sx, sy = ax + ux * (bw / 2 + 4), ay + uy * (bh / 2 + 4)
        ex, ey = bx - ux * (bw / 2 + 8), by - uy * (bh / 2 + 8)
        to_common = (b == "common")
        stroke = "#cbd5e1" if to_common else "#93c5fd"
        marker = "arrow" if to_common else "arrow-blue"
        sw = 1.0 if to_common else min(1.2 + wgt * 0.5, 5)
        s += line(sx, sy, ex, ey, stroke=stroke, sw=sw, marker=marker)
        if not to_common:  # label cross-domain coupling
            mxx, myy = (sx + ex) / 2, (sy + ey) / 2
            s += (f'<circle cx="{mxx:.0f}" cy="{myy:.0f}" r="9" fill="#eff6ff" '
                  f'stroke="#93c5fd" stroke-width="1"/>\n')
            s += text(mxx, myy + 4, wgt, 11, "#1d4ed8", weight="bold")

    # nodes
    for m in MODULES:
        x, y = pos[m]
        if m == "common":
            fill, stroke, tcol = "#fde68a", "#d97706", "#7c2d12"
        elif m == "order":
            fill, stroke, tcol = "#fee2e2", "#ef4444", "#7f1d1d"
        else:
            fill, stroke, tcol = "#e0f2fe", "#0284c7", "#0c4a6e"
        s += box(x - bw / 2, y - bh / 2, bw, bh, fill, stroke, rx=10, sw=2)
        s += text(x, y + 5, m, 14, tcol, weight="bold")

    # legend
    ly = H - 40
    s += box(28, ly - 18, 14, 14, "#e0f2fe", "#0284c7", rx=3)
    s += text(50, ly - 6, "domain context", 12, "#334155", anchor="start")
    s += box(190, ly - 18, 14, 14, "#fde68a", "#d97706", rx=3)
    s += text(212, ly - 6, "common (shared kernel)", 12, "#334155", anchor="start")
    s += box(400, ly - 18, 14, 14, "#fee2e2", "#ef4444", rx=3)
    s += text(422, ly - 6, "order = unreachable (no controller/callers)", 12, "#334155", anchor="start")
    s += text(28, ly + 18,
              "Note: `common` also imports product/inventory/media (metrics & cache glue) - the shared "
              "kernel is not a pure leaf.", 11, "#64748b", anchor="start")
    s += "</svg>\n"
    return s


# --------------------------------------------------------------------------- #
# 2. reservation write path
# --------------------------------------------------------------------------- #
def reservation_diagram():
    W, H = 940, 1020
    s = svg_header(W, H, "Idempotent inventory reservation - write path")
    s += text(W / 2, 34, "Write path: POST /api/v1/inventory/reservations", 20, "#0f172a", weight="bold")
    s += text(W / 2, 56, "Filter chain + transactional service, as wired in the source (X-Idempotency-Key required)",
              12, "#64748b")

    cx = W / 2
    bw = 560
    x = cx - bw / 2
    y = 84
    gap = 20

    def stage(label, sub, fill, stroke, h=54, tcol="#0f172a"):
        nonlocal y, s
        s += box(x, y, bw, h, fill, stroke, rx=8, sw=1.6)
        s += text(cx, y + (24 if sub else h / 2 + 4), label, 14, tcol, weight="bold")
        if sub:
            s += text(cx, y + 42, sub, 11, "#475569")
        top = y
        y += h + gap
        return top

    def connector():
        nonlocal s
        s += line(cx, y - gap, cx, y, stroke="#334155", sw=1.6)

    stage("Client", "POST body {variantId, warehouseId, quantity, referenceId}  +  X-Idempotency-Key",
          "#f1f5f9", "#94a3b8")
    connector()
    stage("MdcRequestFilter   @Order(HIGHEST_PRECEDENCE)", "assigns request id -> MDC + X-Request-Id",
          "#ecfeff", "#06b6d4")
    connector()
    stage("SecurityHeadersFilter   @Order(+1)", "X-Content-Type-Options, X-Frame-Options, CSP, (HSTS in prod)",
          "#ecfeff", "#06b6d4")
    connector()
    stage("ApiKeyAuthFilter   @Order(+2)", "X-Api-Key checked on writes when catalog.security.require-api-key=true",
          "#ecfeff", "#06b6d4")
    connector()
    stage("RateLimitingFilter", "token bucket - writes 20/min per IP (Redis; Caffeine fallback if Redis down)",
          "#ecfeff", "#06b6d4")
    connector()

    # highlighted atomic claim stage (the M4 fix)
    s += box(x - 10, y, bw + 20, 78, "#fef9c3", "#ca8a04", rx=10, sw=2.4)
    s += text(cx, y + 22, "IdempotencyFilter - ATOMIC CLAIM (the M4 fix)", 15, "#713f12", weight="bold")
    s += text(cx, y + 42, "SET idempotency:<method:path:identity:key>  <in-progress>  NX PX 30s", 12, "#854d0e")
    s += text(cx, y + 60,
              "claim won -> execute  |  claim lost -> replay 2xx or 409  |  Redis down on /inventory -> 503 (fail-closed)",
              11, "#854d0e")
    claim_bottom = y + 78
    y += 78 + gap
    connector()

    stage("InventoryController.reserveStock", "maps request -> service call", "#eef2ff", "#6366f1")
    connector()

    # transaction boundary block
    tb_top = y
    s += box(x - 24, y, bw + 48, 250, "#f8fafc", "#1e293b", rx=12, sw=2)
    s += text(cx, y + 22, "InventoryService.reserveStock", 15, "#0f172a", weight="bold")
    s += text(cx, y + 40, "@Transactional  +  @Retryable(OptimisticLockingFailureException, backoff x1.5)",
              11, "#475569")
    inner = x
    iy = y + 56
    steps = [
        ("1. load Inventory (variant_id, warehouse_id)", "#e2e8f0"),
        ("2. existing ACTIVE reservation for referenceId?  ->  return it (idempotent)", "#e2e8f0"),
        ("3. inventory.reserve(qty)  ->  save  (@Version optimistic lock)", "#dbeafe"),
        ("4. save InventoryReservation  (DB unique idx uq_ir_active_inventory_reference)", "#dbeafe"),
        ("5. append InventoryJournal  (append-only audit; RESERVE)", "#dbeafe"),
        ("6. publish domain events", "#e2e8f0"),
    ]
    for lbl, col in steps:
        s += box(inner + 16, iy, bw + 16 - 32 + 16, 26, col, "#94a3b8", rx=5, sw=1)
        s += text(cx, iy + 17, lbl, 11.5, "#0f172a")
        iy += 30
    s += text(cx, tb_top + 250 - 10, "COMMIT  (all-or-nothing transaction boundary)", 12, "#166534",
              weight="bold")
    y = tb_top + 250 + gap
    connector()

    stage("IdempotencyFilter - record outcome", "cache 2xx body 24h; release claim on non-2xx  (retry -> X-Idempotency-Replayed: true)",
          "#fef9c3", "#ca8a04")
    connector()
    stage("201 Created", "Location: /api/v1/inventory/reservations/{id}", "#dcfce7", "#16a34a", tcol="#166534")

    s += "</svg>\n"
    return s


# --------------------------------------------------------------------------- #
# 3. ERD
# --------------------------------------------------------------------------- #
def erd_diagram():
    W, H = 1080, 900
    s = svg_header(W, H, "Core catalog ER model")
    s += text(W / 2, 30, "Core catalog entity-relationship model", 20, "#0f172a", weight="bold")
    s += text(W / 2, 50, "From @Entity classes + Flyway V1..V17.  All catalog tables carry BaseEntity: "
                          "id(UUID), version, created_at, updated_at, deleted_at (soft delete).",
              11.5, "#64748b")

    def draw_entity(name, x, y, w, fields, header="#1d4ed8"):
        nonlocal s
        h = 26 + 18 * len(fields) + 8
        s += box(x, y, w, h, "#ffffff", "#334155", rx=6, sw=1.6)
        s += box(x, y, w, 24, header, header, rx=6, sw=0)
        s += (f'<rect x="{x}" y="{y+14}" width="{w}" height="10" fill="{header}"/>\n')
        s += text(x + w / 2, y + 17, name, 13, "#ffffff", weight="bold")
        fy = y + 42
        for f in fields:
            s += text(x + 10, fy, f, 11, "#334155", anchor="start")
            fy += 18
        return (x, y, w, h)

    boxes = {}
    boxes["Brand"] = draw_entity("Brand", 40, 90, 165, ["name", "slug (uq)", "active, featured"], "#7c3aed")
    boxes["Category"] = draw_entity("Category", 40, 250, 190,
                                    ["name, slug (uq)", "parent_id -> self", "path, depth"], "#7c3aed")
    boxes["AttributeType"] = draw_entity("AttributeType", 40, 470, 190, ["name (uq)", "data_type", "filterable"],
                                         "#0891b2")
    boxes["AttributeValue"] = draw_entity("AttributeValue", 40, 630, 210,
                                          ["attribute_type_id -> AttributeType", "value, display_value", "hex_code"],
                                          "#0891b2")
    boxes["Product"] = draw_entity("Product", 380, 130, 210,
                                   ["name, slug (uq)", "status", "brand_id -> Brand",
                                    "primary_category_id -> Category"], "#1d4ed8")
    boxes["ProductImage"] = draw_entity("ProductImage", 380, 400, 210,
                                        ["product_id -> Product", "storage_key (S3)", "primary, status"], "#1d4ed8")
    boxes["Variant"] = draw_entity("Variant", 780, 110, 230,
                                   ["product_id -> Product", "internal_sku (uq)", "merchant_sku (uq)",
                                    "base_price, tax_class"], "#1d4ed8")
    boxes["Warehouse"] = draw_entity("Warehouse", 470, 560, 175, ["code (uq)", "type", "country_code"], "#059669")
    boxes["Inventory"] = draw_entity("Inventory", 780, 400, 230,
                                     ["variant_id -> Variant", "warehouse_id -> Warehouse",
                                      "quantity, reserved_qty", "reorder_level"], "#059669")
    boxes["InventoryReservation"] = draw_entity("InventoryReservation", 780, 620, 230,
                                                ["inventory_id -> Inventory", "reference_id (uq active)",
                                                 "quantity, status", "expires_at"], "#059669")
    boxes["InventoryJournal"] = draw_entity("InventoryJournal", 380, 720, 250,
                                            ["inventory_id (denorm.)", "operation_type",
                                             "qty/reserved before-after-delta", "append-only"], "#dc2626")

    def edge(a, b, label, card="1..*", side_a="right", side_b="left", dash=None, color="#334155"):
        nonlocal s
        ax, ay, aw, ah = boxes[a]
        bx, by, bw2, bh = boxes[b]
        pax = ax + (aw if side_a == "right" else 0 if side_a == "left" else aw / 2)
        pay = ay + ah / 2 if side_a in ("left", "right") else (ay + ah if side_a == "bottom" else ay)
        pbx = bx + (bw2 if side_b == "right" else 0 if side_b == "left" else bw2 / 2)
        pby = by + bh / 2 if side_b in ("left", "right") else (by + bh if side_b == "bottom" else by)
        s += line(pax, pay, pbx, pby, stroke=color, sw=1.4, marker="arrow", dash=dash)
        mx, my = (pax + pbx) / 2, (pay + pby) / 2
        s += (f'<rect x="{mx-24}" y="{my-9}" width="48" height="16" rx="3" fill="#ffffff" '
              f'stroke="{color}" stroke-width="0.8"/>\n')
        s += text(mx, my + 3, card, 10, color)

    edge("Brand", "Product", "", "1..*", "right", "left")
    edge("Category", "Product", "", "1..*", "right", "left")
    edge("Category", "Category", "", "self", "bottom", "bottom", dash="4 3", color="#7c3aed")
    edge("AttributeType", "AttributeValue", "", "1..*", "bottom", "top", color="#0891b2")
    edge("Product", "Variant", "", "1..*", "right", "left")
    edge("Product", "ProductImage", "", "1..*", "bottom", "top")
    edge("Variant", "AttributeValue", "", "*..*", "bottom", "right", dash="4 3", color="#0891b2")
    edge("Variant", "Inventory", "", "1..*", "bottom", "top", color="#059669")
    edge("Warehouse", "Inventory", "", "1..*", "right", "left", color="#059669")
    edge("Inventory", "InventoryReservation", "", "1..*", "bottom", "top", color="#059669")
    edge("Inventory", "InventoryJournal", "", "1..*", "left", "right", dash="4 3", color="#dc2626")

    s += text(40, H - 16,
              "*..*  = join table (product_categories, variant_attribute_values).  "
              "Dashed = denormalized / self / many-to-many.  orders/order_line_items exist (V12) but are unreferenced.",
              10.5, "#64748b", anchor="start")
    s += "</svg>\n"
    return s


def main():
    for name, fn in [("module-dependencies.svg", module_diagram),
                     ("reservation-write-path.svg", reservation_diagram),
                     ("erd-core.svg", erd_diagram)]:
        path = os.path.join(OUT, name)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(fn())
        print("wrote", path)


if __name__ == "__main__":
    main()
