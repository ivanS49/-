(function() {
  'use strict';
  
  browser.storage.local.get(['blockWebRtc', 'antiFingerprinting', 'emulateScreen', 'emulatedResolution']).then((settings) => {
    const blockRtc = settings.blockWebRtc !== false; // default true
    const blockFp = settings.antiFingerprinting !== false; // default true
    const emulateScreen = settings.emulateScreen !== false; // default true
    const emulatedResolution = settings.emulatedResolution || '1920x1080';
    
    if (!blockRtc && !blockFp && !emulateScreen) return;
    
    const code = `
      (function() {
        'use strict';
        const shouldBlockRtc = ${blockRtc};
        const shouldBlockFp = ${blockFp};
        const shouldEmulateScreen = ${emulateScreen};
        const emulatedResString = "${emulatedResolution}";
        
        function getSpoofedParams(ua) {
        let targetPlatform = 'MacIntel';
        let targetVendor = 'Apple Computer, Inc.';
        let targetMaxTouchPoints = 0;
        let targetOscpu = 'Intel Mac OS X 10_15_7';
        let targetAppVersion = '5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15';
        const userAgent = ua || (typeof navigator !== 'undefined' ? navigator.userAgent : '');

        if (/Macintosh|Mac OS X/i.test(userAgent)) {
          targetPlatform = 'MacIntel';
          targetVendor = 'Apple Computer, Inc.';
          targetMaxTouchPoints = 0;
          targetOscpu = 'Intel Mac OS X 10_15_7';
          targetAppVersion = '5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15';
        } else if (/Windows|Win64|Win32/i.test(userAgent)) {
          targetPlatform = 'Win32';
          targetVendor = /Chrome|Chromium/i.test(userAgent) ? 'Google Inc.' : '';
          targetMaxTouchPoints = 0;
          targetOscpu = 'Windows NT 10.0; Win64; x64';
          targetAppVersion = '5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
        } else if (/iPhone/i.test(userAgent)) {
          targetPlatform = 'iPhone';
          targetVendor = 'Apple Computer, Inc.';
          targetMaxTouchPoints = 5;
          targetOscpu = 'CPU iPhone OS 17_0 like Mac OS X';
          targetAppVersion = '5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1';
        } else if (/iPad/i.test(userAgent)) {
          targetPlatform = 'iPad';
          targetVendor = 'Apple Computer, Inc.';
          targetMaxTouchPoints = 5;
          targetOscpu = 'CPU OS 17_0 like Mac OS X';
          targetAppVersion = '5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1';
        } else if (/Android/i.test(userAgent)) {
          targetPlatform = 'Linux armv81';
          targetVendor = 'Google Inc.';
          targetMaxTouchPoints = 5;
          targetOscpu = 'Linux armv81';
          targetAppVersion = '5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';
        } else if (/Linux/i.test(userAgent)) {
          targetPlatform = 'Linux x86_64';
          targetVendor = '';
          targetMaxTouchPoints = 0;
          targetOscpu = 'Linux x86_64';
          targetAppVersion = '5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
        }

        return {
          platform: targetPlatform,
          vendor: targetVendor,
          maxTouchPoints: targetMaxTouchPoints,
          oscpu: targetOscpu,
          appVersion: targetAppVersion,
          userAgent: userAgent
        };
      }

      function applyShield(targetWin) {
        if (!targetWin) return;
        try {
          if (shouldBlockRtc) {
            // 1. WebRTC Shield
            const rtcProps = [
              'RTCPeerConnection',
              'webkitRTCPeerConnection',
              'mozRTCPeerConnection',
              'RTCDataChannel',
              'RTCSessionDescription',
              'RTCIceCandidate',
              'MediaStreamTrack'
            ];
            rtcProps.forEach(function(p) {
              try {
                Object.defineProperty(targetWin, p, {
                  value: undefined,
                  writable: false,
                  enumerable: false,
                  configurable: false
                });
              } catch(e) {
                try { targetWin[p] = undefined; } catch(e2) {}
              }
            });

            if (targetWin.navigator && targetWin.navigator.mediaDevices) {
              try {
                Object.defineProperty(targetWin.navigator.mediaDevices, 'getUserMedia', {
                  value: undefined,
                  writable: false,
                  configurable: false
                });
                Object.defineProperty(targetWin.navigator.mediaDevices, 'enumerateDevices', {
                  value: function() { return Promise.resolve([]); },
                  writable: false,
                  configurable: false
                });
              } catch(e) {}
            }
          }

          if (shouldBlockFp) {
            // 2. Anti-Fingerprinting (Navigator)
            const ua = (targetWin.navigator ? targetWin.navigator.userAgent : '');
            const navProps = getSpoofedParams(ua);
            
            if (targetWin.Navigator && targetWin.Navigator.prototype) {
              for (const [prop, val] of Object.entries(navProps)) {
                try {
                  Object.defineProperty(targetWin.Navigator.prototype, prop, {
                    get: function() { return val; },
                    configurable: true,
                    enumerable: true
                  });
                } catch(e) {}
              }
            }

            if (targetWin.navigator) {
              try {
                const navProxy = new Proxy(targetWin.navigator, {
                  get: function(t, p, r) {
                    if (p in navProps) return navProps[p];
                    const res = Reflect.get(t, p, r);
                    return (typeof res === 'function') ? res.bind(t) : res;
                  }
                });
                Object.defineProperty(targetWin, 'navigator', {
                  value: navProxy,
                  configurable: true,
                });
              } catch(e) {}
            }
          }

          if (shouldEmulateScreen) {
            // Screen spoofing based on selected resolution
            let w = 1920, h = 1080, dpr = 1;
            try {
              const parts = emulatedResString.split('x');
              if (parts.length === 2) {
                w = parseInt(parts[0]) || 1920;
                h = parseInt(parts[1]) || 1080;
                
                // Set appropriate dpr based on resolution dimensions usually found on mobile
                if (w <= 450) dpr = 3;
                else if (w <= 800) dpr = 2;
                else if (w > 2000) dpr = 2;
                else dpr = 1;
              }
            } catch(e) {}
            if (targetWin.screen) {
              try { Object.defineProperty(targetWin.screen, 'width', { get: () => w }); } catch(e){}
              try { Object.defineProperty(targetWin.screen, 'height', { get: () => h }); } catch(e){}
              try { Object.defineProperty(targetWin.screen, 'availWidth', { get: () => w }); } catch(e){}
              try { Object.defineProperty(targetWin.screen, 'availHeight', { get: () => h }); } catch(e){}
            }
            try { Object.defineProperty(targetWin, 'devicePixelRatio', { get: () => dpr }); } catch(e){}
            try { Object.defineProperty(targetWin, 'innerWidth', { get: () => w }); } catch(e){}
            try { Object.defineProperty(targetWin, 'innerHeight', { get: () => h }); } catch(e){}
          }

          // 3. Canvas Noise Injection
          if (targetWin.HTMLCanvasElement && targetWin.CanvasRenderingContext2D) {
            const origGetImageData = targetWin.CanvasRenderingContext2D.prototype.getImageData;
            const origToDataURL = targetWin.HTMLCanvasElement.prototype.toDataURL;
            const origToBlob = targetWin.HTMLCanvasElement.prototype.toBlob;

            const addNoise = function(imgData) {
              if (!imgData || !imgData.data) return;
              const d = imgData.data;
              const len = d.length;
              for (let i = 0; i < len; i += 16) {
                const ch = i + (i % 3);
                const val = d[ch];
                if (val === 255) d[ch] = 254;
                else if (val === 0) d[ch] = 1;
                else d[ch] = (val % 2 === 0) ? val + 1 : val - 1;
              }
            };

            targetWin.CanvasRenderingContext2D.prototype.getImageData = function() {
              const res = origGetImageData.apply(this, arguments);
              try { addNoise(res); } catch(e) {}
              return res;
            };

            targetWin.HTMLCanvasElement.prototype.toDataURL = function() {
              try {
                const ctx = this.getContext('2d');
                if (ctx && this.width > 0 && this.height > 0) {
                  const doc = targetWin.document || (typeof document !== 'undefined' ? document : null);
                  if (doc) {
                    const offscreen = doc.createElement('canvas');
                    offscreen.width = this.width;
                    offscreen.height = this.height;
                    const offCtx = offscreen.getContext('2d');
                    offCtx.drawImage(this, 0, 0);
                    const imgData = origGetImageData.call(offCtx, 0, 0, this.width, this.height);
                    addNoise(imgData);
                    offCtx.putImageData(imgData, 0, 0);
                    return origToDataURL.apply(offscreen, arguments);
                  }
                }
              } catch(e) {}
              return origToDataURL.apply(this, arguments);
            };

            if (origToBlob) {
              targetWin.HTMLCanvasElement.prototype.toBlob = function(callback, type, quality) {
                try {
                  const ctx = this.getContext('2d');
                  if (ctx && this.width > 0 && this.height > 0) {
                    const doc = targetWin.document || (typeof document !== 'undefined' ? document : null);
                    if (doc) {
                      const offscreen = doc.createElement('canvas');
                      offscreen.width = this.width;
                      offscreen.height = this.height;
                      const offCtx = offscreen.getContext('2d');
                      offCtx.drawImage(this, 0, 0);
                      const imgData = origGetImageData.call(offCtx, 0, 0, this.width, this.height);
                      addNoise(imgData);
                      offCtx.putImageData(imgData, 0, 0);
                      return origToBlob.call(offscreen, callback, type, quality);
                    }
                  }
                } catch(e) {}
                return origToBlob.apply(this, arguments);
              };
            }

            try {
              targetWin.CanvasRenderingContext2D.prototype.getImageData.toString = function() { return 'function getImageData() { [native code] }'; };
              targetWin.HTMLCanvasElement.prototype.toDataURL.toString = function() { return 'function toDataURL() { [native code] }'; };
              if (origToBlob) {
                targetWin.HTMLCanvasElement.prototype.toBlob.toString = function() { return 'function toBlob() { [native code] }'; };
              }
            } catch(e) {}
          }
        } catch(e) {}
      }

      // Apply to top window immediately
      if (typeof window !== 'undefined') {
        applyShield(window);
      }

      // Intercept dynamic iframes (including about:blank)
      if (typeof HTMLIFrameElement !== 'undefined') {
        const origContentWindow = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'contentWindow');
        if (origContentWindow && origContentWindow.get) {
          Object.defineProperty(HTMLIFrameElement.prototype, 'contentWindow', {
            get: function() {
              const win = origContentWindow.get.call(this);
              if (win) {
                applyShield(win);
              }
              return win;
            },
            configurable: true,
            enumerable: true
          });
        }

        const origContentDoc = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'contentDocument');
        if (origContentDoc && origContentDoc.get) {
          Object.defineProperty(HTMLIFrameElement.prototype, 'contentDocument', {
            get: function() {
              const win = this.contentWindow;
              if (win) {
                applyShield(win);
              }
              return origContentDoc.get.call(this);
            },
            configurable: true,
            enumerable: true
          });
        }
      }

      if (typeof Node !== 'undefined' && Node.prototype) {
        const origAppend = Node.prototype.appendChild;
        Node.prototype.appendChild = function(child) {
          const res = origAppend.apply(this, arguments);
          if (child && child.tagName === 'IFRAME') {
            try {
              if (child.contentWindow) applyShield(child.contentWindow);
              child.addEventListener('load', function() {
                try { if (this.contentWindow) applyShield(this.contentWindow); } catch(e) {}
              });
            } catch(e) {}
          }
          return res;
        };

        const origInsert = Node.prototype.insertBefore;
        Node.prototype.insertBefore = function(newNode, referenceNode) {
          const res = origInsert.apply(this, arguments);
          if (newNode && newNode.tagName === 'IFRAME') {
            try {
              if (newNode.contentWindow) applyShield(newNode.contentWindow);
              newNode.addEventListener('load', function() {
                try { if (this.contentWindow) applyShield(this.contentWindow); } catch(e) {}
              });
            } catch(e) {}
          }
          return res;
        };
      }

      if (typeof Document !== 'undefined' && Document.prototype) {
        const origCreateElement = Document.prototype.createElement;
        Document.prototype.createElement = function(tagName, options) {
          const el = origCreateElement.apply(this, arguments);
          if (typeof tagName === 'string' && tagName.toLowerCase() === 'iframe') {
            el.addEventListener('load', function() {
              try { if (this.contentWindow) applyShield(this.contentWindow); } catch(e) {}
            });
          }
          return el;
        };
      }
    })();
  `;

  try {
    const script = document.createElement('script');
    script.textContent = code;
    (document.head || document.documentElement).appendChild(script);
    script.remove();
  } catch (e) {}

  try {
    if (window.wrappedJSObject) {
      ['RTCPeerConnection', 'webkitRTCPeerConnection', 'mozRTCPeerConnection'].forEach(function(p) {
        try {
          Object.defineProperty(window.wrappedJSObject, p, {
            value: undefined,
            writable: false,
            configurable: false
          });
        } catch (e) {
          window.wrappedJSObject[p] = undefined;
        }
      });
    }
  } catch (e) {}
  });
})();