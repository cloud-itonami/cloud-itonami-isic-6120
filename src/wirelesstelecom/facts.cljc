(ns wirelesstelecom.facts
  "Per-jurisdiction mobile-spectrum-licensing + subscriber-identity
  regulatory catalog -- the G2-style spec-basis table the Mobile
  Network Governor checks every identity/verify proposal against ('did
  the advisor cite an OFFICIAL public source for this jurisdiction's
  spectrum-licensing/subscriber-registration authority, or did it
  invent one?').

  This is the wireless-specific analog of `telecom.facts`
  (`cloud-itonami-isic-6190`, the sibling wired/VoIP-reseller telecom
  actor in this fleet): the SAME per-jurisdiction spec-basis discipline,
  but citing the jurisdiction's RADIO-SPECTRUM-LICENSING statute (not a
  fixed-line numbering-plan rule) -- a genuinely distinct regulatory
  concern from 6190's own scope, matching this repo's own
  `docs/business-model.md` Offer: 'spectrum-license and site-access
  scope management'.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official spectrum
  regulator (see `:provenance`); they are a STARTING catalog, not a
  from-scratch survey of all ~194 jurisdictions. Extending coverage is
  additive: add one map to `catalog`, cite a real source, done -- never
  invent a jurisdiction's requirements to make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  subscriber-identity-verification-record/msisdn-assignment-record/
  spectrum-license-assignment-record/service-suspension-log evidence
  set submitted in some form; `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2 citation the governor requires before any
  `:identity/verify` proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "総務省 (MIC, Ministry of Internal Affairs and Communications)"
          :legal-basis "電波法 (Radio Act) / 携帯電話不正利用防止法 (Mobile Phone Improper Use Prevention Act)"
          :national-spec "移動体通信事業者の周波数割当及び契約者確認に関する規律"
          :provenance "https://www.soumu.go.jp/"
          :required-evidence ["契約者確認記録 (subscriber-identity-verification-record)"
                              "MSISDN割当記録 (msisdn-assignment-record)"
                              "周波数割当証明 (spectrum-license-assignment-record)"
                              "回線停止台帳 (service-suspension-log)"]}
   "USA" {:name "United States"
          :owner-authority "Federal Communications Commission (FCC) / Wireless Telecommunications Bureau"
          :legal-basis "Communications Act of 1934, Title III (47 U.S.C. §301 et seq., radio spectrum licensing) / 47 CFR Part 64 Subpart U (CPNI)"
          :national-spec "FCC spectrum-license and subscriber-identity-verification requirements for mobile carriers"
          :provenance "https://www.fcc.gov/wireless"
          :required-evidence ["Subscriber-identity-verification record"
                              "MSISDN-assignment record"
                              "Spectrum-license-assignment record"
                              "Service-suspension log"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Office of Communications (Ofcom)"
          :legal-basis "Wireless Telegraphy Act 2006 / Communications Act 2003"
          :national-spec "UK spectrum-licence and mobile subscriber registration requirements"
          :provenance "https://www.ofcom.org.uk/spectrum"
          :required-evidence ["Subscriber-identity-verification record"
                              "MSISDN-assignment record"
                              "Spectrum-license-assignment record"
                              "Service-suspension log"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesnetzagentur"
          :legal-basis "Telekommunikationsgesetz (TKG) -- Frequenzzuteilung / Prepaid-SIM-Identifizierungspflicht (TKG §172)"
          :national-spec "Frequenzzuteilung und Teilnehmerregistrierung für Mobilfunk"
          :provenance "https://www.bundesnetzagentur.de/DE/Fachthemen/Telekommunikation/Frequenzen/"
          :required-evidence ["Teilnehmeridentitätsprüfungsnachweis (subscriber-identity-verification-record)"
                              "MSISDN-Zuteilungsnachweis (msisdn-assignment-record)"
                              "Frequenzzuteilungsnachweis (spectrum-license-assignment-record)"
                              "Sperrprotokoll (service-suspension-log)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to provision an
  MSISDN or suspend service on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-6120 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `wirelesstelecom.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
