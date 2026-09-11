(ns wirelesstelecom.facts
  "Reference facts for mobile-network-operator infrastructure
  coordination: build-status vocabulary for tower/base-station
  deployment and maintenance records, equipment-procurement categories
  and their escalation cost thresholds, and a site-operation-type
  reference set. Pure lookup functions for domain reference data -- the
  Governor and Advisor consult these instead of inventing thresholds.
  Mirrors `tobaccoops.facts` (cloud-itonami-isic-0115) in shape, adapted
  to a spectrum-licensed mobile network operator: a site (cell tower /
  base station) is built and maintained (rather than planted and
  harvested), and its recognized build states span planning through
  decommissioning rather than a leaf-quality grade.

  DELIBERATE SCOPE LIMIT (see `docs/adr/0002-site-spectrum-scope-
  correction.md`): this namespace carries NO jurisdiction-specific
  spectrum regulator name, statute citation, or license-fee schedule.
  This repo's own pre-existing README.md / docs/business-model.md /
  docs/operator-guide.md (published before any implementation) never
  cited one, and a prior implementation attempt on this repo added
  invented per-country regulator/statute citations (a Japanese Radio
  Act citation, a US Communications Act of 1934 citation, a UK Wireless
  Telegraphy Act citation, a German TKG citation) that were NOT actually
  verified and were not present anywhere in this repo's own published
  docs -- that content has been removed. The Governor's spectrum-license
  hard check (`wirelesstelecom.governor`) is therefore purely
  STRUCTURAL: a site's own recorded `:spectrum-license-status` must
  literally equal `:active` before a tower may be proposed for
  activation. It does not, and must not, encode any specific
  jurisdiction's licensing regime -- extending this namespace with a
  real regulator's requirements is future work that needs actual
  verification, not something this actor fabricates.")

(def build-status-codes
  "Closed set of recognized build-status codes a network-build record's
  :build-status may cite -- independently verified by the Governor."
  #{"planned" "in-progress" "commissioned" "operational"
    "maintenance-hold" "decommissioned"})

(defn build-status-known? [status]
  (contains? build-status-codes status))

(def equipment-categories
  "Procurement categories this actor may propose orders for, and the
  default cost threshold above which an order proposal must escalate
  for human sign-off (site engineer / ops manager). Mobile-network
  infrastructure's distinctive high-cost categories are backhaul
  (microwave/fibre uplink) and power-system (site generator/battery)
  equipment, priced above routine antenna/RF-hardware line items."
  {"antenna"
   {:id "antenna" :name "アンテナ" :cost-threshold 300}

   "rf-equipment"
   {:id "rf-equipment" :name "無線機器" :cost-threshold 800}

   "backhaul-equipment"
   {:id "backhaul-equipment" :name "バックホール設備" :cost-threshold 2000}

   "power-system"
   {:id "power-system" :name "電源設備" :cost-threshold 1500}})

(defn equipment-category-by-id [id]
  (get equipment-categories id))

(def default-cost-threshold
  "Fallback escalation threshold used when an order-equipment proposal
  doesn't cite a known category (never invent a lower bar than this)."
  500)

(def site-operation-types
  "Reference set of site-operation types this actor's
  schedule-site-operation proposals commonly cover: robotics-assisted
  tower/antenna deployment, base-station maintenance, and site
  inspection (matching this repo's own README `Robotics premise`
  paragraph), plus upgrade and decommissioning. Informational only --
  NOT a validated enum; the advisor/operator may propose other
  operation-type strings and the Governor does not reject unlisted
  values here."
  #{"deployment" "maintenance" "inspection" "upgrade" "decommissioning"})
