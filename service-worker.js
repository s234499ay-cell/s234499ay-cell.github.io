const CACHE_NAME = 'yaqeen-v2';

const APP_SHELL = [
    './',
    './index.html',
    './manifest.json',
    './icon-192.png',
    './icon-512.png'
];

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => cache.addAll(APP_SHELL))
            .then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(
                keys
                    .filter(key => key !== CACHE_NAME)
                    .map(key => caches.delete(key))
            ))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', event => {
    const request = event.request;

    if (request.method !== 'GET') {
        return;
    }

    const url = new URL(request.url);

    // ملفات التطبيق: الشبكة أولًا (لتصل التحديثات)، ثم النسخة المحفوظة عند انقطاع الإنترنت
    if (url.origin === self.location.origin) {
        event.respondWith(
            fetch(request)
                .then(response => {
                    const copy = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(request, copy));
                    return response;
                })
                .catch(() =>
                    caches.match(request).then(cached =>
                        cached || caches.match('./index.html')
                    )
                )
        );
        return;
    }

    // مكتبة Supabase من CDN: النسخة المحفوظة أولًا حتى يفتح التطبيق بدون إنترنت
    if (url.hostname === 'cdn.jsdelivr.net') {
        event.respondWith(
            caches.match(request).then(cached => {
                if (cached) {
                    return cached;
                }

                return fetch(request).then(response => {
                    const copy = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(request, copy));
                    return response;
                });
            })
        );
    }

    // أي طلبات أخرى (Supabase والتلاوات الصوتية) تمر مباشرة بدون تدخل
});
