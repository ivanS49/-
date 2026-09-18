// WebRTC Shield & Network Throttling Background Script

let config = {
  slow3g: false,
  slow3gDelay: 1200,
  blockWebRtc: true,
  antiFingerprinting: true,
  emulateScreen: true,
  emulatedResolution: '1920x1080'
};

function applyConfig(data) {
  if (!data || typeof data !== 'object') return;
  if (typeof data.slow3g === 'boolean') config.slow3g = data.slow3g;
  if (typeof data.slow3gDelay === 'number') config.slow3gDelay = Math.max(0, data.slow3gDelay);
  
  if (typeof data.blockWebRtc === 'boolean') config.blockWebRtc = data.blockWebRtc;
  if (typeof data.antiFingerprinting === 'boolean') config.antiFingerprinting = data.antiFingerprinting;
  if (typeof data.emulateScreen === 'boolean') config.emulateScreen = data.emulateScreen;
  if (typeof data.emulatedResolution === 'string') config.emulatedResolution = data.emulatedResolution;
  
  try {
    if (typeof browser !== 'undefined' && browser.privacy && browser.privacy.network) {
      if (browser.privacy.network.peerConnectionEnabled) {
        browser.privacy.network.peerConnectionEnabled.set({ value: !config.blockWebRtc });
      }
    }
  } catch (e) {}

  try {
    if (typeof browser !== 'undefined' && browser.storage && browser.storage.local) {
      browser.storage.local.set({
        blockWebRtc: config.blockWebRtc,
        antiFingerprinting: config.antiFingerprinting,
        emulateScreen: config.emulateScreen,
        emulatedResolution: config.emulatedResolution
      });
    }
  } catch (e) {}
  console.log('Config updated:', config);
}

// Intercept network requests and simulate network delay
if (typeof browser !== 'undefined' && browser.webRequest && browser.webRequest.onBeforeRequest) {
  browser.webRequest.onBeforeRequest.addListener(
    function(details) {
      // Exclude internal resources and schemes
      if (!details || !details.url) return {};
      const url = details.url;
      if (url.startsWith('resource://') ||
          url.startsWith('moz-extension://') ||
          url.startsWith('about:') ||
          url.startsWith('data:') ||
          url.startsWith('blob:')) {
        return {};
      }

      if (config.slow3g && config.slow3gDelay > 0) {
        return new Promise(function(resolve) {
          setTimeout(function() {
            resolve({});
          }, config.slow3gDelay);
        });
      }
      return {};
    },
    { urls: ['<all_urls>'] },
    ['blocking']
  );
}

// 1. Native Messaging (Direct Port communication with Kotlin GeckoRuntime)
let nativePort = null;

function connectNativePort() {
  if (typeof browser === 'undefined' || !browser.runtime || !browser.runtime.connectNative) {
    return;
  }
  try {
    nativePort = browser.runtime.connectNative('browser');
    nativePort.onMessage.addListener(function(msg) {
      applyConfig(msg);
    });
    nativePort.onDisconnect.addListener(function() {
      nativePort = null;
      setTimeout(connectNativePort, 2000);
    });
    // Request initial config
    nativePort.postMessage({ type: 'getConfig' });
  } catch (e) {
    console.warn('connectNative error:', e);
    queryNativeConfig();
  }
}

function queryNativeConfig() {
  if (typeof browser === 'undefined' || !browser.runtime || !browser.runtime.sendNativeMessage) {
    return;
  }
  try {
    browser.runtime.sendNativeMessage('browser', { type: 'getConfig' })
      .then(function(resp) {
        applyConfig(resp);
      })
      .catch(function(err) {
        console.warn('sendNativeMessage error:', err);
      });
  } catch (e) {}
}

// 2. browser.runtime.sendMessage listener (from content scripts or extension callers)
if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.onMessage) {
  browser.runtime.onMessage.addListener(function(msg, sender, sendResponse) {
    if (msg && typeof msg === 'object') {
      applyConfig(msg);
      if (typeof sendResponse === 'function') {
        sendResponse({ status: 'ok', config: config });
      }
    }
    return true;
  });
}

// 3. browser.storage.local sync if available
try {
  if (typeof browser !== 'undefined' && browser.storage && browser.storage.local) {
    browser.storage.local.get(['slow3g', 'slow3gDelay']).then(function(items) {
      if (items) applyConfig(items);
    });
    if (browser.storage.onChanged) {
      browser.storage.onChanged.addListener(function(changes, area) {
        if (area === 'local') {
          const updated = {};
          if (changes.slow3g) updated.slow3g = changes.slow3g.newValue;
          if (changes.slow3gDelay) updated.slow3gDelay = changes.slow3gDelay.newValue;
          applyConfig(updated);
        }
      });
    }
  }
} catch (e) {}

connectNativePort();
