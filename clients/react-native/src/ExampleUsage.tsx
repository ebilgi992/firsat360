/**
 * Örnek: market rafı → CatalogBridge. Feed (https://ebilgi992.github.io/firsat360/feed.json) "client_bridge" alanını
 * taşır; bu alan varsa derleme anındaki BRIDGE_MARKETS yerine o kullanılır (adres değişince uygulama güncellemesi gerekmez).
 */
import React, { useEffect, useState } from 'react';
import { FlatList, Modal, Pressable, SafeAreaView, Text } from 'react-native';
import CatalogBridge from './CatalogBridge';
import { BRIDGE_MARKETS, BridgeEntry } from './bridgeScripts';

const FEED_URL = 'https://ebilgi992.github.io/firsat360/feed.json';
const NAMES: Record<string, string> = {
  a101: 'A101', sok: 'ŞOK', migros: 'Migros', gratis: 'Gratis', carrefoursa: 'CarrefourSA', rossmann: 'Rossmann',
};

type FeedBridge = Record<string, { url: string; profile: string; allowed_hosts: string[] }>;

export default function BrochureShelf() {
  const [markets, setMarkets] = useState<Record<string, BridgeEntry>>(BRIDGE_MARKETS);
  const [open, setOpen] = useState<string | null>(null);

  useEffect(() => {
    fetch(FEED_URL)
      .then((r) => r.json())
      .then((feed: { client_bridge?: FeedBridge }) => {
        const cb = feed.client_bridge;
        if (!cb) return;
        const next: Record<string, BridgeEntry> = {};
        for (const [id, e] of Object.entries(cb)) {
          if (typeof e?.url === 'string' && e.url.startsWith('https://') && Array.isArray(e.allowed_hosts)) {
            next[id] = { url: e.url, profile: e.profile, allowedHosts: e.allowed_hosts };
          }
        }
        if (Object.keys(next).length) setMarkets(next);
      })
      .catch(() => undefined); // çevrimdışı: derleme anındaki yapılandırma
  }, []);

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: '#0B0D12' }}>
      <FlatList
        data={Object.keys(markets)}
        keyExtractor={(id) => id}
        contentContainerStyle={{ padding: 16, gap: 12 }}
        renderItem={({ item: id }) => (
          <Pressable onPress={() => setOpen(id)} accessibilityRole="button"
            style={{ backgroundColor: '#161A24', borderRadius: 14, padding: 18 }}>
            <Text style={{ color: '#F2F3F7', fontSize: 17, fontWeight: '700' }}>{NAMES[id] ?? id}</Text>
            <Text style={{ color: 'rgba(242,243,247,0.7)', marginTop: 4 }}>Güncel broşür</Text>
          </Pressable>
        )}
      />
      <Modal visible={open !== null} animationType="slide" onRequestClose={() => setOpen(null)}>
        {open && (
          <CatalogBridge targetMarket={open} entry={markets[open]} marketName={NAMES[open]} onClose={() => setOpen(null)} />
        )}
      </Modal>
    </SafeAreaView>
  );
}
