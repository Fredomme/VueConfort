export const languages = ['fr', 'en'] as const;
export type Lang = typeof languages[number];

export const slugs = [
  'fonctionnalites', 'tests-visuels', 'profils-intelligents', 'lecteur', 'faq',
  'support', 'telechargement', 'confidentialite', 'mentions-legales', 'contact',
  'a-propos', 'blog', 'presse'
] as const;
export type Slug = typeof slugs[number];

export const routes: Record<Lang, Record<Slug, string>> = {
  fr: Object.fromEntries(slugs.map((slug) => [slug, slug])) as Record<Slug, string>,
  en: {
    fonctionnalites: 'features', 'tests-visuels': 'visual-tests',
    'profils-intelligents': 'smart-profiles', lecteur: 'reader', faq: 'faq',
    support: 'support', telechargement: 'download', confidentialite: 'privacy',
    'mentions-legales': 'legal-notice', contact: 'contact', 'a-propos': 'about',
    blog: 'blog', presse: 'press'
  }
};

export const reverseRoutes = Object.fromEntries(languages.map((lang) => [
  lang,
  Object.fromEntries(Object.entries(routes[lang]).map(([key, value]) => [value, key]))
])) as Record<Lang, Record<string, Slug>>;

export const ui = {
  fr: {
    skip: 'Aller au contenu', nav: 'Navigation principale', menu: 'Menu', close: 'Fermer',
    theme: 'Changer de thème', lang: 'English', discover: 'Découvrir les fonctionnalités',
    android: 'Télécharger Android', iphone: 'iPhone — bientôt disponible',
    local: '100 % local', noTracking: 'Aucun suivi', accessible: 'Conçu pour être accessible',
    soon: 'Bientôt disponible', notify: 'Être informé', learn: 'En savoir plus',
    disclaimer: 'VueConfort est un outil de confort et de dépistage sur écran. Il ne fournit ni diagnostic ni ordonnance et ne remplace pas un professionnel de santé.',
    footer: 'Votre écran s’adapte à vos yeux, pas l’inverse.',
    rights: 'Tous droits réservés.', contact: 'Contact', privacy: 'Confidentialité', legal: 'Mentions légales'
  },
  en: {
    skip: 'Skip to content', nav: 'Main navigation', menu: 'Menu', close: 'Close',
    theme: 'Change theme', lang: 'Français', discover: 'Explore features',
    android: 'Download for Android', iphone: 'iPhone — coming soon',
    local: '100% on-device', noTracking: 'No tracking', accessible: 'Designed for accessibility',
    soon: 'Coming soon', notify: 'Get notified', learn: 'Learn more',
    disclaimer: 'VueConfort is an on-screen comfort and screening tool. It does not provide a diagnosis or prescription and does not replace an eye-care professional.',
    footer: 'Your screen adapts to your eyes, not the other way around.',
    rights: 'All rights reserved.', contact: 'Contact', privacy: 'Privacy', legal: 'Legal notice'
  }
} as const;

export const navItems: Record<Lang, Array<[Slug, string]>> = {
  fr: [['fonctionnalites','Fonctionnalités'],['tests-visuels','Tests visuels'],['profils-intelligents','Profils'],['lecteur','Lecteur'],['blog','Blog'],['support','Support']],
  en: [['fonctionnalites','Features'],['tests-visuels','Visual tests'],['profils-intelligents','Profiles'],['lecteur','Reader'],['blog','Blog'],['support','Support']]
};

type Section = { title: string; body: string; points?: string[] };
type Page = { title: string; eyebrow: string; description: string; intro: string; sections: Section[] };

export const pages: Record<Lang, Record<Slug, Page>> = {
  fr: {
    fonctionnalites: { title:'Un confort visuel qui vous ressemble', eyebrow:'Fonctionnalités', description:'Découvrez les fonctions de VueConfort : tests visuels, calibration, profils, lecteur adapté et confidentialité locale.', intro:'VueConfort réunit des outils simples et cohérents pour rendre la lecture sur écran plus confortable, sans compte ni cloud.', sections:[
      {title:'Tests visuels standardisés',body:'Landolt C, E directionnel, contraste et grille d’Amsler suivent un parcours guidé avec contrôle des conditions.',points:['Calibration physique de l’écran','Mesures OD, OG et OU','Historique local et fiabilité explicite']},
      {title:'Profils adaptés',body:'Les résultats et vos préférences peuvent générer des réglages de confort que vous gardez libres de modifier.',points:['Grossissement','Lisibilité et espacement','Profils selon l’usage']},
      {title:'Lecture intelligente',body:'Le lecteur adapte taille, interligne, contraste et thème. Sur Android, il peut utiliser le texte accessible exposé par l’application à votre demande.'},
      {title:'Android et iPhone',body:'VueConfort est conçu comme une expérience cohérente sur mobile. Les capacités système diffèrent selon la plateforme et sont expliquées sans promesse impossible.'},
      {title:'Privé par conception',body:'Aucun compte, serveur, tracker, analytics ou publicité. Les informations restent sur l’appareil.'}
    ]},
    'tests-visuels': { title:'Des tests guidés, des limites claires', eyebrow:'Tests visuels', description:'Comprendre les tests Landolt C, E directionnel, contraste, Amsler et la calibration physique VueConfort.', intro:'Les tests VueConfort sont des outils de dépistage sur écran. Ils aident à observer une situation, jamais à établir un diagnostic.', sections:[
      {title:'Landolt C',body:'Un anneau interrompu s’affiche dans différentes orientations. Vous indiquez la direction de l’ouverture pour estimer une acuité affichable.'},
      {title:'E directionnel',body:'La direction des branches du E change. Cette alternative limite la dépendance à la reconnaissance de lettres.'},
      {title:'Sensibilité au contraste',body:'Des symboles progressivement moins contrastés évaluent le seuil visible dans des conditions lumineuses déclarées stables.'},
      {title:'Grille d’Amsler',body:'La grille aide à signaler lignes ondulées, zones manquantes, sombres ou floues. Une apparition récente ou soudaine justifie un avis professionnel rapide.'},
      {title:'Calibration physique',body:'La taille réelle de l’écran, sa densité, la distance confirmée et l’orientation conditionnent les calculs. Une mesure non fiable est signalée comme telle.'},
      {title:'Historique et profils',body:'Les rapports restent localement sur l’appareil. Un profil de confort peut être proposé à partir des résultats, puis ajusté ou supprimé.'}
    ]},
    'profils-intelligents': { title:'Le bon profil au bon moment', eyebrow:'Profils intelligents', description:'Profils VueConfort pour la lecture, les petits textes, l’extérieur et les usages personnalisés.', intro:'Un profil rassemble les réglages de grossissement, visibilité et lecture. Vous contrôlez toujours son activation.', sections:[
      {title:'Des bases immédiatement utiles',body:'Standard, Petit texte, Lecture, Lecture longue, Extérieur et Personnalisé couvrent des contextes courants sans imposer un réglage unique.'},
      {title:'Automatisations locales',body:'Sur Android, une règle peut sélectionner un profil selon l’application active, une plage horaire ou la luminosité ambiante.',points:['Priorités réglables','Suspension manuelle','Aucune donnée envoyée']},
      {title:'Toujours réversible',body:'Activez, dupliquez, modifiez, restaurez ou supprimez un profil. Les profils prédéfinis peuvent retrouver leurs valeurs initiales.'}
    ]},
    lecteur: { title:'Lire avec moins de friction', eyebrow:'Lecteur VueConfort', description:'Le lecteur VueConfort adapte localement la présentation du texte pour améliorer le confort de lecture.', intro:'Le lecteur privilégie une mise en page stable, lisible et réglable plutôt qu’une accumulation d’effets.', sections:[
      {title:'Présentation adaptée',body:'Taille du texte, interligne, espacement, fond clair, sombre ou contraste renforcé sont disponibles dans une interface directe.'},
      {title:'Lecture depuis une autre application',body:'Sur Android, le bouton Lire utilise uniquement le texte et les descriptions accessibles fournis par l’application courante. Aucune capture d’écran ni OCR global.'},
      {title:'Limites respectées',body:'Jeux, vidéos, images, WebViews et écrans protégés peuvent ne fournir aucun texte. VueConfort ne contourne pas ces protections.'}
    ]},
    faq: { title:'Questions fréquentes', eyebrow:'FAQ', description:'Réponses aux questions fréquentes sur VueConfort, les tests, la confidentialité et la compatibilité.', intro:'Des réponses directes sur ce que fait VueConfort — et sur ce qu’il ne fait pas.', sections:[
      {title:'VueConfort remplace-t-il mes lunettes ?',body:'Non. L’application améliore la présentation de l’écran mais ne corrige pas une anomalie visuelle.'},
      {title:'Les tests donnent-ils un diagnostic ?',body:'Non. Ils servent au dépistage sur écran. Un résultat inhabituel ou une gêne persistante doit être discuté avec un professionnel.'},
      {title:'Mes données quittent-elles mon téléphone ?',body:'Non. VueConfort n’utilise ni serveur, compte, publicité, analytics ou permission Internet.'},
      {title:'Pourquoi un service d’accessibilité sur Android ?',body:'Il permet les commandes flottantes, le grossissement système, les règles par application et la lecture locale du texte accessible à votre demande.'},
      {title:'Pourquoi certains textes ne sont-ils pas lisibles ?',body:'L’application affichée peut ne pas exposer de texte accessible, notamment pour une image, une vidéo, un jeu ou un écran protégé.'},
      {title:'VueConfort fonctionne-t-il hors ligne ?',body:'Oui. Les fonctions essentielles sont conçues pour fonctionner entièrement sur l’appareil.'}
    ]},
    support: { title:'Nous sommes là pour vous aider', eyebrow:'Support', description:'Support officiel VueConfort : aide, problèmes fréquents, compatibilité, confidentialité et contacts prévus.', intro:'Consultez d’abord les solutions rapides, puis écrivez-nous avec le modèle du téléphone et la version Android ou iOS.', sections:[
      {title:'La barre Android n’apparaît pas',body:'Vérifiez que le service VueConfort est actif dans Réglages > Accessibilité et que la batterie Samsung ne place pas l’application en veille profonde.'},
      {title:'Le grossissement ne change pas',body:'Réinitialisez le grossissement puis réactivez le service. Android peut limiter le contrôleur lorsqu’un autre service l’utilise.'},
      {title:'Lire ne trouve aucun texte',body:'L’application courante ne fournit probablement pas de contenu accessible. VueConfort ne capture pas l’écran pour le remplacer.'},
      {title:'Compatibilité et limites',body:'Android autorise un service d’accessibilité pour le grossissement et les commandes flottantes. iOS ne permet ni bulle, ni lecture libre des autres applications, ni filtre global.'},
      {title:'Contacts prévus',body:'Assistance : support@vueconfort.fr · Confidentialité : privacy@vueconfort.fr · Demandes générales : contact@vueconfort.fr. Ces adresses doivent être confirmées par votre configuration de messagerie.'}
    ]},
    telechargement: { title:'VueConfort sur votre téléphone', eyebrow:'Téléchargement', description:'Disponibilité de VueConfort pour Android et état du développement de l’application iPhone.', intro:'Les versions officielles seront distribuées par Google Play et l’App Store lorsqu’elles seront prêtes. Aucun téléchargement direct non vérifié n’est proposé.', sections:[
      {title:'Android · bientôt sur Google Play',body:'Statut : préparation avant lancement public. Version actuelle du projet : 0.1.0. Android 9 (API 28) minimum ; un appareil Android récent est recommandé, avec une expérience optimisée en priorité pour le Galaxy S25. Google Play n’est pas encore public et aucun APK public signé n’est proposé.'},
      {title:'Application iPhone en cours de développement',body:'Fonctions prévues : tests visuels, profils, lecteur interne, import de texte et PDF, extension de partage. iOS ne permet ni bulle globale, ni lecture libre du contenu des autres applications, ni filtre global.'},
      {title:'Installation sûre',body:'N’installez pas d’APK ou de profil provenant d’un site tiers. Vérifiez toujours que l’éditeur et le domaine officiel correspondent à VueConfort.'}
    ]},
    confidentialite: { title:'Votre vie privée reste sur votre appareil', eyebrow:'Confidentialité', description:'Politique de confidentialité VueConfort : aucune collecte, aucun serveur, aucun compte, aucune publicité ni analytics.', intro:'VueConfort est conçu pour fonctionner localement. Le site n’utilise ni cookie, tracker, analytics, pixel publicitaire ni formulaire distant.', sections:[
      {title:'Données traitées dans l’application',body:'Profils, réglages, calibrations, résultats et historiques des tests, règles automatiques et texte accessible demandé peuvent être traités localement.'},
      {title:'Aucune transmission',body:'Aucun compte, serveur, cloud, publicité, suivi ou analytics. VueConfort n’ajoute aucun transfert réseau.'},
      {title:'Service d’accessibilité Android',body:'Il contrôle le grossissement, affiche les commandes, détecte l’application active pour vos règles et extrait à la demande le texte accessible. Aucun mot de passe, écran ou contenu n’est enregistré ou transmis.'},
      {title:'Suppression',body:'L’application permet d’effacer séparément historique, profils, règles ou tous les réglages. La désinstallation supprime normalement son stockage privé.'},
      {title:'Contact confidentialité',body:'Pour toute question : privacy@vueconfort.fr.'}
    ]},
    'mentions-legales': { title:'Mentions légales', eyebrow:'Informations', description:'Mentions légales du site officiel VueConfort.', intro:'Informations relatives au site vueconfort.fr et à son fonctionnement.', sections:[
      {title:'Édition',body:'VueConfort est un projet édité en France. Contact officiel : contact@vueconfort.fr. Les informations d’identité et d’immatriculation obligatoires seront publiées selon le statut juridique effectif de l’éditeur avant toute exploitation commerciale.'},
      {title:'Hébergement',body:'Le site statique peut être hébergé par Cloudflare Pages, GitHub Pages ou Vercel. L’hébergeur effectivement retenu et ses coordonnées seront indiqués ici lors de la mise en ligne.'},
      {title:'Propriété intellectuelle',body:'La marque, les textes, l’interface et les éléments graphiques VueConfort sont protégés. Les bibliothèques open source conservent leurs licences respectives.'},
      {title:'Responsabilité',body:'Les informations du site sont générales et ne constituent pas un conseil médical. VueConfort ne fournit ni diagnostic ni ordonnance.'}
    ]},
    contact: { title:'Parlons de VueConfort', eyebrow:'Contact', description:'Contacter VueConfort pour le support, la confidentialité, la presse ou une question générale.', intro:'Choisissez l’adresse adaptée ou préparez votre message avec le formulaire local ci-dessous.', sections:[
      {title:'Contact général',body:'contact@vueconfort.fr'}, {title:'Support',body:'support@vueconfort.fr'}, {title:'Confidentialité',body:'privacy@vueconfort.fr'},
      {title:'Formulaire sans collecte',body:'Le formulaire ouvre votre logiciel de messagerie. Rien n’est envoyé ou stocké par ce site.'}
    ]},
    'a-propos': { title:'La technologie au service du confort', eyebrow:'À propos', description:'Découvrez pourquoi VueConfort existe, sa mission et ses principes.', intro:'VueConfort part d’une idée simple : un écran peut mieux respecter les préférences de lecture de chacun.', sections:[
      {title:'Pourquoi VueConfort existe',body:'Le projet est né d’un besoin concret : rendre la lecture sur écran plus confortable pour les personnes qui ont du mal avec les petits caractères, sans transformer l’application en dispositif médical.'},
      {title:'Notre mission',body:'Rendre les outils de confort visuel compréhensibles, réglables et disponibles sans sacrifier la vie privée.'},
      {title:'Nos principes',body:'Utilité réelle, limites explicites, fonctionnement local, accessibilité et contrôle utilisateur guident chaque décision.'},
      {title:'Pas une promesse médicale',body:'VueConfort aide à observer et à adapter l’affichage. Il ne remplace jamais des lunettes, un opticien ou un ophtalmologue.'}
    ]},
    blog: { title:'Mieux comprendre le confort sur écran', eyebrow:'Blog', description:'Actualités et conseils VueConfort sur la fatigue visuelle, la lecture numérique et l’accessibilité.', intro:'Des contenus clairs, prudents et documentés pour mieux utiliser les écrans au quotidien.', sections:[
      {title:'Fatigue visuelle · Adapter son environnement',body:'Distance, reflets, pauses et taille d’affichage peuvent modifier le confort ressenti. Commencez par des changements simples et réversibles.'},
      {title:'Accessibilité · Pourquoi le texte accessible compte',body:'Lorsqu’une application décrit correctement son interface, les lecteurs d’écran et outils d’assistance peuvent mieux accompagner chacun.'},
      {title:'Conseils · Lire plus longtemps sans forcer',body:'Augmenter légèrement la taille, stabiliser l’interligne et réduire les reflets vaut souvent mieux qu’un contraste extrême.'},
      {title:'VueConfort · La confidentialité par défaut',body:'Une fonction utile n’a pas besoin de transformer vos habitudes de lecture en données marketing.'}
    ]},
    presse: { title:'Espace presse', eyebrow:'Presse', description:'Informations presse officielles sur VueConfort, sa mission, ses fonctions et ses contacts.', intro:'Les éléments ci-dessous peuvent être cités pour présenter fidèlement VueConfort.', sections:[
      {title:'Présentation courte',body:'VueConfort est une application mobile d’assistance au confort de lecture qui réunit profils d’affichage, lecteur local et outils de dépistage sur écran.'},
      {title:'Points clés',body:'Fonctionnement local, absence de compte et de suivi, limites médicales explicites, accessibilité et personnalisation.'},
      {title:'Contact presse',body:'contact@vueconfort.fr · Objet recommandé : Presse — VueConfort'},
      {title:'Usage de la marque',body:'Merci de ne pas modifier le nom VueConfort ni de présenter l’application comme un dispositif de diagnostic ou une alternative aux lunettes.'}
    ]}
  },
  en: {} as Record<Slug, Page>
};

pages.en = {
  fonctionnalites:{title:'Visual comfort that feels personal',eyebrow:'Features',description:'Explore VueConfort visual tests, calibration, profiles, adapted reader and on-device privacy.',intro:'VueConfort brings together clear, consistent tools to make screen reading more comfortable—with no account or cloud.',sections:[{title:'Standardised visual tests',body:'Landolt C, directional E, contrast and Amsler grid follow a guided flow with condition checks.',points:['Physical display calibration','Right, left and both-eye measurements','On-device history with explicit reliability']},{title:'Adapted profiles',body:'Results and preferences can generate comfort settings that you remain free to change.',points:['Magnification','Readability and spacing','Profiles for different situations']},{title:'Intelligent reading',body:'The reader adapts size, line spacing, contrast and theme. On Android it can use accessibility text exposed by an app when you ask.'},{title:'Android and iPhone',body:'VueConfort is designed as a coherent mobile experience. System capabilities vary by platform and are explained without impossible promises.'},{title:'Private by design',body:'No account, server, tracker, analytics or advertising. Information stays on the device.'}]},
  'tests-visuels':{title:'Guided tests, clear limits',eyebrow:'Visual tests',description:'Understand VueConfort Landolt C, directional E, contrast, Amsler and physical calibration tests.',intro:'VueConfort tests are on-screen screening tools. They can help observe a situation, never establish a diagnosis.',sections:[{title:'Landolt C',body:'A broken ring appears in different orientations. You indicate the gap direction to estimate displayable acuity.'},{title:'Directional E',body:'The E arms change direction. This alternative reduces reliance on letter recognition.'},{title:'Contrast sensitivity',body:'Progressively lower-contrast symbols estimate a visible threshold under declared stable lighting.'},{title:'Amsler grid',body:'The grid helps report wavy lines and missing, dark or blurred areas. A recent or sudden change calls for prompt professional advice.'},{title:'Physical calibration',body:'Real screen size, density, confirmed distance and orientation affect calculations. Unreliable measurement is reported as such.'},{title:'History and profiles',body:'Reports remain on-device. A comfort profile may be suggested, then adjusted or deleted.'}]},
  'profils-intelligents':{title:'The right profile at the right time',eyebrow:'Smart profiles',description:'VueConfort profiles for reading, small text, outdoors and personalised use.',intro:'A profile groups magnification, visibility and reading settings. You always control activation.',sections:[{title:'Useful starting points',body:'Standard, Small text, Reading, Long reading, Outdoor and Custom cover common situations without imposing one setting.'},{title:'On-device automation',body:'On Android, a rule can select a profile by active app, time range or ambient light.',points:['Adjustable priorities','Manual pause','No data sent']},{title:'Always reversible',body:'Activate, duplicate, change, restore or delete a profile. Presets can return to their initial values.'}]},
  lecteur:{title:'Read with less friction',eyebrow:'VueConfort Reader',description:'VueConfort Reader adapts text presentation locally for more comfortable reading.',intro:'The reader favours a stable, legible and adjustable layout over unnecessary effects.',sections:[{title:'Adapted presentation',body:'Text size, line spacing, character spacing, light, dark or reinforced-contrast backgrounds are available directly.'},{title:'Reading from another app',body:'On Android, Read uses only accessibility text and descriptions supplied by the current app. No screenshot or global OCR.'},{title:'Protections respected',body:'Games, videos, images, web views and protected screens may expose no text. VueConfort does not bypass those protections.'}]},
  faq:{title:'Frequently asked questions',eyebrow:'FAQ',description:'Answers about VueConfort, visual tests, privacy and compatibility.',intro:'Direct answers about what VueConfort does—and what it does not do.',sections:[{title:'Does VueConfort replace my glasses?',body:'No. The app improves screen presentation but does not correct a visual condition.'},{title:'Do the tests give a diagnosis?',body:'No. They are on-screen screening tools. Discuss unusual results or persistent discomfort with a professional.'},{title:'Does my data leave my phone?',body:'No. VueConfort uses no server, account, advertising, analytics or Internet permission.'},{title:'Why an Android accessibility service?',body:'It enables floating controls, system magnification, per-app rules and local reading of accessibility text on request.'},{title:'Why can some text not be read?',body:'The displayed app may expose no accessibility text, especially for images, videos, games or protected screens.'},{title:'Does it work offline?',body:'Yes. Essential functions are designed to operate entirely on-device.'}]},
  support:{title:'We are here to help',eyebrow:'Support',description:'Official VueConfort support: common issues, compatibility, privacy and planned contacts.',intro:'Check these quick solutions first, then write with your phone model and Android or iOS version.',sections:[{title:'The Android bar does not appear',body:'Check that VueConfort is enabled in Settings > Accessibility and that Samsung battery settings do not put it into deep sleep.'},{title:'Magnification does not change',body:'Reset magnification, then re-enable the service. Android may limit the controller while another service uses it.'},{title:'Read finds no text',body:'The current app probably exposes no accessibility content. VueConfort does not capture the screen as a substitute.'},{title:'Compatibility and limits',body:'Android allows an accessibility service for magnification and floating controls. iOS allows no global bubble, unrestricted reading of other apps or global filter.'},{title:'Planned contacts',body:'Support: support@vueconfort.fr · Privacy: privacy@vueconfort.fr · General: contact@vueconfort.fr. Availability depends on the domain email configuration.'}]},
  telechargement:{title:'VueConfort on your phone',eyebrow:'Download',description:'VueConfort availability for Android and current iPhone development status.',intro:'Official versions will be distributed through Google Play and the App Store when ready. No unverified direct download is offered.',sections:[{title:'Android · coming soon to Google Play',body:'Status: pre-launch preparation. Current project version: 0.1.0. Android 9 (API 28) minimum; a recent Android device is recommended, with the Galaxy S25 as the primary optimisation target. Google Play is not public yet and no public signed APK is offered.'},{title:'iPhone app in development',body:'Planned features: visual tests, profiles, internal reader, text and PDF import, and a share extension. iOS allows no global bubble, unrestricted reading of other apps or global filter.'},{title:'Safe installation',body:'Do not install APKs or profiles from third-party sites. Always verify the official VueConfort publisher and domain.'}]},
  confidentialite:{title:'Your privacy stays on your device',eyebrow:'Privacy',description:'VueConfort privacy policy: no collection, server, account, advertising or analytics.',intro:'VueConfort is designed to work locally. This website uses no cookie, tracker, analytics, advertising pixel or remote form.',sections:[{title:'Data processed in the app',body:'Profiles, settings, calibrations, test results and history, automation rules and requested accessibility text may be processed locally.'},{title:'No transmission',body:'No account, server, cloud, advertising, tracking or analytics. VueConfort adds no network transfer.'},{title:'Android accessibility service',body:'It controls magnification, shows commands, detects the active app for your rules and extracts accessibility text on request. No password, screen or content is recorded or transmitted.'},{title:'Deletion',body:'The app can clear history, profiles, rules or all settings separately. Uninstalling normally removes private storage.'},{title:'Privacy contact',body:'Questions: privacy@vueconfort.fr.'}]},
  'mentions-legales':{title:'Legal notice',eyebrow:'Information',description:'Legal notice for the official VueConfort website.',intro:'Information about vueconfort.fr and how it operates.',sections:[{title:'Publisher',body:'VueConfort is a project published in France. Official contact: contact@vueconfort.fr. Required identity and registration information will be published according to the publisher’s effective legal status before commercial operation.'},{title:'Hosting',body:'This static site can be hosted by Cloudflare Pages, GitHub Pages or Vercel. The selected host and legal details will be stated here when the site goes live.'},{title:'Intellectual property',body:'The VueConfort name, copy, interface and visual elements are protected. Open-source libraries retain their respective licences.'},{title:'Liability',body:'Site information is general and not medical advice. VueConfort provides no diagnosis or prescription.'}]},
  contact:{title:'Let’s talk about VueConfort',eyebrow:'Contact',description:'Contact VueConfort for support, privacy, press or a general question.',intro:'Choose the relevant address or prepare your message with the local form below.',sections:[{title:'General enquiries',body:'contact@vueconfort.fr'},{title:'Support',body:'support@vueconfort.fr'},{title:'Privacy',body:'privacy@vueconfort.fr'},{title:'No-collection form',body:'The form opens your email application. Nothing is sent to or stored by this website.'}]},
  'a-propos':{title:'Technology serving comfort',eyebrow:'About',description:'Discover why VueConfort exists, its mission and principles.',intro:'VueConfort starts with a simple idea: a screen can better respect each person’s reading preferences.',sections:[{title:'Why VueConfort exists',body:'The project grew from a concrete need: make on-screen reading more comfortable for people who struggle with small text, without turning the app into a medical device.'},{title:'Our mission',body:'Make visual comfort tools understandable, adjustable and available without sacrificing privacy.'},{title:'Our principles',body:'Real usefulness, explicit limits, on-device operation, accessibility and user control guide every decision.'},{title:'Not a medical promise',body:'VueConfort helps observe and adapt display. It never replaces glasses, an optician or an ophthalmologist.'}]},
  blog:{title:'Understand screen comfort',eyebrow:'Blog',description:'VueConfort news and guidance on visual fatigue, digital reading and accessibility.',intro:'Clear, careful content for better everyday screen use.',sections:[{title:'Visual fatigue · Adapt your environment',body:'Distance, glare, breaks and display size can affect comfort. Start with simple, reversible changes.'},{title:'Accessibility · Why accessible text matters',body:'When apps describe interfaces correctly, screen readers and assistance tools can support more people.'},{title:'Guidance · Read longer without straining',body:'A slightly larger size, stable line spacing and fewer reflections often help more than extreme contrast.'},{title:'VueConfort · Privacy by default',body:'A useful feature does not need to turn reading habits into marketing data.'}]},
  presse:{title:'Press room',eyebrow:'Press',description:'Official press information about VueConfort, its mission, features and contacts.',intro:'The following can be quoted to describe VueConfort accurately.',sections:[{title:'Short description',body:'VueConfort is a mobile reading-comfort assistance app combining display profiles, a local reader and on-screen screening tools.'},{title:'Key points',body:'On-device operation, no account or tracking, explicit medical limits, accessibility and personalisation.'},{title:'Press contact',body:'contact@vueconfort.fr · Suggested subject: Press — VueConfort'},{title:'Brand use',body:'Please do not alter the VueConfort name or describe the app as a diagnostic device or alternative to glasses.'}]}
};

export const home = {
  fr: { title:'VueConfort', subtitle:'Votre écran s’adapte à vos yeux, pas l’inverse.', description:'VueConfort facilite la lecture sur mobile avec le grossissement Android, des profils personnalisés, un lecteur adapté et des tests visuels de dépistage.', intro:'VueConfort vous aide à lire plus confortablement grâce au grossissement Android, aux profils personnalisés, au lecteur adapté et aux tests visuels de dépistage.' },
  en: { title:'VueConfort', subtitle:'Your screen adapts to your eyes, not the other way around.', description:'VueConfort supports comfortable mobile reading with Android magnification, personal profiles, an adapted reader and visual screening tests.', intro:'VueConfort helps you read more comfortably with Android magnification, personal profiles, an adapted reader and visual screening tests.' }
};
