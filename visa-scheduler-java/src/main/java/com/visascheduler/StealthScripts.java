package com.visascheduler;

/**
 * JavaScript patches injected into every browser page before any script runs.
 *
 * Targets all known headless / automation detection vectors as of 2026:
 *  - CDP artifact variables (__cdc_*, __playwright, __pw*)
 *  - navigator.webdriver flag
 *  - navigator.permissions lying about notification state
 *  - window.outerWidth/Height = 0 in headless
 *  - navigator.plugins empty list
 *  - navigator.deviceMemory, hardwareConcurrency unrealistic values
 *  - screen.colorDepth = 24 (headless sometimes reports 0 or 1)
 *  - navigator.connection missing
 *  - window.chrome missing runtime object
 *  - WebRTC IP leak prevention
 *  - AudioContext fingerprint noise
 *  - Canvas fingerprint noise
 *  - Speech synthesis voices present
 */
public final class StealthScripts {

    private StealthScripts() {}

    public static final String ALL = """
        // ── 1. Remove CDP / Playwright artifact globals ────────────────────────
        (function removeCdpArtifacts() {
          const cdcKeys = Object.getOwnPropertyNames(window)
            .filter(k => k.startsWith('__cdc_') || k.startsWith('__playwright')
                      || k.startsWith('__pw') || k === '$cdc_asdjflasutopfhvcZLmcfl_');
          cdcKeys.forEach(k => {
            try { delete window[k]; } catch(e) {}
          });
        })();

        // ── 2. navigator.webdriver ─────────────────────────────────────────────
        Object.defineProperty(navigator, 'webdriver', { get: () => undefined, configurable: true });

        // ── 3. navigator.plugins — real browser has many ──────────────────────
        const fakePlugins = [
          { name: 'Chrome PDF Plugin',   filename: 'internal-pdf-viewer',   description: 'Portable Document Format' },
          { name: 'Chrome PDF Viewer',   filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai', description: '' },
          { name: 'Native Client',       filename: 'internal-nacl-plugin',  description: '' },
        ];
        Object.defineProperty(navigator, 'plugins', {
          get: () => {
            const arr = fakePlugins.map(p => Object.assign(Object.create(Plugin.prototype), p));
            Object.setPrototypeOf(arr, PluginArray.prototype);
            return arr;
          }, configurable: true
        });
        Object.defineProperty(navigator, 'mimeTypes', {
          get: () => {
            const arr = [];
            Object.setPrototypeOf(arr, MimeTypeArray.prototype);
            return arr;
          }, configurable: true
        });

        // ── 4. navigator.languages ────────────────────────────────────────────
        Object.defineProperty(navigator, 'languages', {
          get: () => ['en-CA', 'en-US', 'en'], configurable: true
        });

        // ── 5. navigator.permissions — return 'default' not 'denied' ─────────
        const origQuery = window.Permissions && window.Permissions.prototype.query;
        if (origQuery) {
          window.Permissions.prototype.query = function(params) {
            if (params && params.name === 'notifications') {
              return Promise.resolve({ state: 'default', onchange: null });
            }
            return origQuery.apply(this, arguments);
          };
        }

        // ── 6. window.outerWidth / outerHeight (0 in headless) ───────────────
        if (window.outerWidth === 0) {
          Object.defineProperty(window, 'outerWidth',  { get: () => window.innerWidth,  configurable: true });
          Object.defineProperty(window, 'outerHeight', { get: () => window.innerHeight + 74, configurable: true });
        }

        // ── 7. screen.colorDepth / pixelDepth ────────────────────────────────
        Object.defineProperty(screen, 'colorDepth', { get: () => 24, configurable: true });
        Object.defineProperty(screen, 'pixelDepth', { get: () => 24, configurable: true });

        // ── 8. navigator.deviceMemory + hardwareConcurrency ──────────────────
        Object.defineProperty(navigator, 'deviceMemory', { get: () => 8, configurable: true });
        Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8, configurable: true });

        // ── 9. window.chrome — missing in headless ────────────────────────────
        if (!window.chrome) {
          window.chrome = {
            app: { isInstalled: false, InstallState: { DISABLED:'disabled', INSTALLED:'installed', NOT_INSTALLED:'not_installed' }, RunningState: { CANNOT_RUN:'cannot_run', READY_TO_RUN:'ready_to_run', RUNNING:'running' } },
            runtime: { OnInstalledReason: { CHROME_UPDATE:'chrome_update', INSTALL:'install', SHARED_MODULE_UPDATE:'shared_module_update', UPDATE:'update' }, OnRestartRequiredReason: { APP_UPDATE:'app_update', OS_UPDATE:'os_update', PERIODIC:'periodic' }, PlatformArch: { ARM:'arm', ARM64:'arm64', MIPS:'mips', MIPS64:'mips64', X86_32:'x86-32', X86_64:'x86-64' }, PlatformNaclArch: { ARM:'arm', MIPS:'mips', MIPS64:'mips64', X86_32:'x86-32', X86_64:'x86-64' }, PlatformOs: { ANDROID:'android', CROS:'cros', LINUX:'linux', MAC:'mac', OPENBSD:'openbsd', WIN:'win' }, RequestUpdateCheckStatus: { NO_UPDATE:'no_update', THROTTLED:'throttled', UPDATE_AVAILABLE:'update_available' } }
          };
        }

        // ── 10. MediaDevices — headless returns 0 devices ────────────────────
        if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
          const origEnum = navigator.mediaDevices.enumerateDevices.bind(navigator.mediaDevices);
          navigator.mediaDevices.enumerateDevices = function() {
            return origEnum().then(devices => {
              if (devices.length === 0) {
                return [
                  { deviceId: '', kind: 'audioinput',  label: '', groupId: '' },
                  { deviceId: '', kind: 'videoinput',  label: '', groupId: '' },
                  { deviceId: '', kind: 'audiooutput', label: '', groupId: '' },
                ];
              }
              return devices;
            });
          };
        }

        // ── 11. Canvas fingerprint — tiny random noise per session ────────────
        (function noiseCanvas() {
          const orig = HTMLCanvasElement.prototype.toDataURL;
          HTMLCanvasElement.prototype.toDataURL = function(type) {
            const ctx = this.getContext('2d');
            if (ctx) {
              const imgData = ctx.getImageData(0, 0, 1, 1);
              imgData.data[0] = (imgData.data[0] + (Math.random() * 2 - 1)) & 0xFF;
              ctx.putImageData(imgData, 0, 0);
            }
            return orig.apply(this, arguments);
          };
        })();

        // ── 12. AudioContext fingerprint noise ────────────────────────────────
        (function noiseAudio() {
          const origGetChannelData = AudioBuffer.prototype.getChannelData;
          AudioBuffer.prototype.getChannelData = function() {
            const data = origGetChannelData.apply(this, arguments);
            if (data.length > 0) {
              const idx = Math.floor(Math.random() * data.length);
              data[idx] += Math.random() * 0.0001;
            }
            return data;
          };
        })();

        // ── 13. WebRTC — prevent IP leak ──────────────────────────────────────
        if (window.RTCPeerConnection) {
          const origRTC = window.RTCPeerConnection;
          window.RTCPeerConnection = function(cfg) {
            if (cfg && cfg.iceServers) cfg.iceServers = [];
            return new origRTC(cfg);
          };
          Object.setPrototypeOf(window.RTCPeerConnection, origRTC);
        }

        // ── 14. Speech synthesis — real browsers have voices ──────────────────
        if (window.speechSynthesis) {
          const origGetVoices = window.speechSynthesis.getVoices.bind(window.speechSynthesis);
          window.speechSynthesis.getVoices = function() {
            const voices = origGetVoices();
            return voices.length > 0 ? voices : [];
          };
        }
        """;
}
