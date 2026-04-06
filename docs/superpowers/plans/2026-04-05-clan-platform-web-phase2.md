# Clan Platform Website — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Next.js website that displays clan data from the Fastify API — drops, collection log, PB hiscores, player profiles, achievements, events, and bingo — with Discord OAuth login and a dark gold/black OSRS theme.

**Architecture:** Next.js 15 App Router with server components for initial page loads and TanStack Query for interactive client-side sections. Auth via Discord OAuth with JWT tokens stored in HTTP-only cookies. The Fastify API at `http://localhost:3001` provides all data.

**Tech Stack:** Next.js 15, TypeScript, Tailwind CSS v4, shadcn/ui, TanStack Query, Cinzel font, lucide-react

---

## File Structure

```
C:/dev/clan-platform-web/
  .env.local
  tailwind.config.ts
  next.config.ts
  package.json
  tsconfig.json
  src/
    app/
      globals.css                       # Tailwind imports + OSRS theme CSS variables
      layout.tsx                        # Root layout: fonts, theme, providers
      page.tsx                          # Landing page (/)
      not-found.tsx                     # Global 404
      auth/
        discord/
          callback/route.ts             # OAuth callback route handler
      api/
        auth/
          me/route.ts                   # Returns current user from cookie
          logout/route.ts               # Clears auth cookies
      [slug]/
        layout.tsx                      # Clan layout: nav, clan context
        not-found.tsx                   # Clan 404
        page.tsx                        # Clan home
        drops/page.tsx
        collection-log/
          page.tsx                      # Leaderboard
          [rsn]/page.tsx                # Player clog
        hiscores/page.tsx
        players/
          page.tsx                      # Roster
          [rsn]/page.tsx                # Player profile
        achievements/page.tsx
        events/page.tsx
        bingo/page.tsx
        settings/page.tsx               # Auth required
        admin/page.tsx                  # Auth + permission required
    components/
      ui/                               # shadcn/ui (auto-generated)
      nav.tsx
      drop-feed.tsx
      drop-row.tsx
      item-icon.tsx
      leaderboard-table.tsx
      player-link.tsx
      event-card.tsx
      bingo-board.tsx
      stat-bar.tsx
      type-badge.tsx
      countdown.tsx
    lib/
      api.ts                            # Server-side fetch helper
      api-client.ts                     # Client-side fetch for TanStack Query
      auth.ts                           # Cookie helpers, JWT verify
      format.ts                         # Number/time formatting
      constants.ts                      # URLs, theme constants
      types.ts                          # API response types
    hooks/
      use-auth.ts
    providers/
      query-provider.tsx
      auth-provider.tsx
    middleware.ts                        # Auth check for protected routes
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `C:/dev/clan-platform-web/package.json`
- Create: `C:/dev/clan-platform-web/.env.local`
- Create: `C:/dev/clan-platform-web/src/app/layout.tsx`
- Create: `C:/dev/clan-platform-web/src/app/globals.css`
- Create: `C:/dev/clan-platform-web/src/app/page.tsx`
- Create: `C:/dev/clan-platform-web/src/lib/constants.ts`

- [ ] **Step 1: Create the Next.js project**

```bash
cd C:/dev
npx create-next-app@latest clan-platform-web --typescript --tailwind --eslint --app --src-dir --import-alias "@/*" --use-npm
```

When prompted, accept all defaults. This creates the project with Next.js 15, TypeScript, Tailwind CSS v4, App Router, and `src/` directory.

- [ ] **Step 2: Install dependencies**

```bash
cd C:/dev/clan-platform-web
npm install @tanstack/react-query jose
npm install -D @tanstack/eslint-plugin-query
```

- [ ] **Step 3: Initialize shadcn/ui**

```bash
cd C:/dev/clan-platform-web
npx shadcn@latest init
```

When prompted:
- Style: Default
- Base color: Slate
- CSS variables: Yes

- [ ] **Step 4: Add shadcn components we'll need**

```bash
cd C:/dev/clan-platform-web
npx shadcn@latest add button card table input select badge tabs separator dropdown-menu avatar
```

- [ ] **Step 5: Create `.env.local`**

Create `C:/dev/clan-platform-web/.env.local`:

```env
API_URL=http://localhost:3001
NEXT_PUBLIC_API_URL=http://localhost:3001
DISCORD_CLIENT_ID=placeholder
DISCORD_REDIRECT_URI=http://localhost:3000/auth/discord/callback
JWT_SECRET=c26f104401b9ccfdddc9a7b128389962d29b1445e35ca2c5b1c7b97d391353e5
```

Note: JWT_SECRET must match the API's secret so the website can verify tokens.

- [ ] **Step 6: Create constants file**

Create `C:/dev/clan-platform-web/src/lib/constants.ts`:

```typescript
export const API_URL = process.env.API_URL ?? "http://localhost:3001";
export const PUBLIC_API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:3001";
export const RUNELITE_CDN = "https://static.runelite.net/cache/item/icon";
export const DISCORD_CLIENT_ID = process.env.DISCORD_CLIENT_ID ?? "";
export const DISCORD_REDIRECT_URI = process.env.DISCORD_REDIRECT_URI ?? "";
export const JWT_SECRET = process.env.JWT_SECRET ?? "";
```

- [ ] **Step 7: Create API response types**

Create `C:/dev/clan-platform-web/src/lib/types.ts`:

```typescript
export interface Drop {
  id: string;
  itemName: string;
  itemId: number | null;
  value: number;
  monsterName: string;
  killCount: number | null;
  screenshotUrl: string | null;
  createdAt: string;
  rsn: string;
}

export interface Player {
  id: string;
  rsn: string;
  verified: boolean;
  joinedAt: string;
}

export interface PlayerProfile {
  player: Player;
  recentDrops: {
    itemName: string;
    value: number;
    monsterName: string;
    createdAt: string;
  }[];
  collectionLogCount: number;
  personalBests: {
    bossKey: string;
    teamSize: number;
    timeMs: number;
  }[];
  recentAchievements: {
    type: string;
    detail: string;
    createdAt: string;
  }[];
}

export interface ClogLeaderboardEntry {
  rsn: string;
  uniqueCount: number;
}

export interface ClogEntry {
  id: string;
  itemName: string;
  itemId: number | null;
  obtainedAt: string;
}

export interface PbLeaderboardEntry {
  bossKey: string;
  teamSize: number;
  rsn: string;
  timeMs: number;
}

export interface PbRanking {
  rsn: string;
  timeMs: number;
  createdAt: string;
}

export interface Achievement {
  id: string;
  type: string;
  detail: string;
  createdAt: string;
  rsn: string;
}

export interface AuthUser {
  id: string;
  username: string;
  avatarUrl: string | null;
}
```

- [ ] **Step 8: Create server-side API fetch helper**

Create `C:/dev/clan-platform-web/src/lib/api.ts`:

```typescript
import { API_URL } from "./constants";

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
    next: { revalidate: 30 },
  });

  if (!res.ok) {
    throw new Error(`API error: ${res.status} ${res.statusText}`);
  }

  return res.json();
}
```

- [ ] **Step 9: Create client-side API fetch helper**

Create `C:/dev/clan-platform-web/src/lib/api-client.ts`:

```typescript
import { PUBLIC_API_URL } from "./constants";

export async function apiClientFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${PUBLIC_API_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });

  if (!res.ok) {
    throw new Error(`API error: ${res.status} ${res.statusText}`);
  }

  return res.json();
}
```

- [ ] **Step 10: Create format utilities**

Create `C:/dev/clan-platform-web/src/lib/format.ts`:

```typescript
export function formatValue(value: number): string {
  if (value >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1)}B`;
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`;
  return value.toLocaleString();
}

export function formatTime(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  const millis = Math.floor((ms % 1000) / 10);
  return `${minutes}:${seconds.toString().padStart(2, "0")}.${millis.toString().padStart(2, "0")}`;
}

export function formatRelativeTime(dateStr: string): string {
  const now = Date.now();
  const then = new Date(dateStr).getTime();
  const diff = now - then;
  const seconds = Math.floor(diff / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(dateStr).toLocaleDateString();
}
```

- [ ] **Step 11: Set up the OSRS theme**

Replace `C:/dev/clan-platform-web/src/app/globals.css` with:

```css
@import "tailwindcss";

@theme {
  --color-background: #0a0a0a;
  --color-card: #111111;
  --color-card-elevated: #1a1a1a;
  --color-primary: #d4a017;
  --color-primary-hover: #b8860b;
  --color-primary-highlight: #f5c842;
  --color-foreground: #e8e0d4;
  --color-foreground-muted: #8a8070;
  --color-border: #2a2211;
  --color-border-active: #d4a017;
  --color-success: #22c55e;
  --color-destructive: #ef4444;
  --color-info: #3b82f6;
  --color-rare: #a855f7;
  --font-heading: "Cinzel", serif;
}

body {
  background-color: var(--color-background);
  color: var(--color-foreground);
}
```

- [ ] **Step 12: Set up root layout with fonts and providers**

Create `C:/dev/clan-platform-web/src/providers/query-provider.tsx`:

```tsx
"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";

export function QueryProvider({ children }: { children: ReactNode }) {
  const [client] = useState(() => new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30 * 1000,
        refetchOnWindowFocus: false,
      },
    },
  }));

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
```

Replace `C:/dev/clan-platform-web/src/app/layout.tsx` with:

```tsx
import type { Metadata } from "next";
import { Inter, Cinzel } from "next/font/google";
import { QueryProvider } from "@/providers/query-provider";
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });
const cinzel = Cinzel({ subsets: ["latin"], variable: "--font-cinzel" });

export const metadata: Metadata = {
  title: "Clan Platform",
  description: "OSRS Clan Management Platform",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="dark">
      <body className={`${inter.variable} ${cinzel.variable} font-sans antialiased`}>
        <QueryProvider>
          {children}
        </QueryProvider>
      </body>
    </html>
  );
}
```

- [ ] **Step 13: Create the landing page**

Replace `C:/dev/clan-platform-web/src/app/page.tsx` with:

```tsx
import Link from "next/link";

export default function LandingPage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center">
      <h1 className="font-heading text-5xl text-primary mb-4">Clan Platform</h1>
      <p className="text-foreground-muted text-lg mb-8">
        OSRS clan management — drops, hiscores, collection log, and more.
      </p>
      <Link
        href="/solus"
        className="rounded-lg bg-primary px-6 py-3 text-background font-semibold hover:bg-primary-hover transition-colors"
      >
        Visit Solus
      </Link>
    </main>
  );
}
```

- [ ] **Step 14: Create global 404 page**

Create `C:/dev/clan-platform-web/src/app/not-found.tsx`:

```tsx
import Link from "next/link";

export default function NotFound() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center">
      <h1 className="font-heading text-4xl text-primary mb-4">404</h1>
      <p className="text-foreground-muted mb-6">Page not found.</p>
      <Link href="/" className="text-primary hover:text-primary-highlight underline">
        Back to home
      </Link>
    </main>
  );
}
```

- [ ] **Step 15: Verify it builds and runs**

```bash
cd C:/dev/clan-platform-web
npm run build
npm run dev
```

Visit `http://localhost:3000` — should see the landing page with gold heading "Clan Platform" and a "Visit Solus" link on a dark background.

- [ ] **Step 16: Initialize git and commit**

```bash
cd C:/dev/clan-platform-web
git init
git add .
git commit -m "feat: project scaffolding — Next.js + Tailwind + shadcn/ui + OSRS theme"
```

---

### Task 2: Shared Components

**Files:**
- Create: `src/components/item-icon.tsx`
- Create: `src/components/player-link.tsx`
- Create: `src/components/type-badge.tsx`
- Create: `src/components/drop-row.tsx`
- Create: `src/components/leaderboard-table.tsx`
- Create: `src/components/stat-bar.tsx`

- [ ] **Step 1: Create the ItemIcon component**

Create `C:/dev/clan-platform-web/src/components/item-icon.tsx`:

```tsx
"use client";

import { useState } from "react";
import Image from "next/image";
import { RUNELITE_CDN } from "@/lib/constants";

interface ItemIconProps {
  itemId: number | null;
  itemName: string;
  size?: number;
}

export function ItemIcon({ itemId, itemName, size = 32 }: ItemIconProps) {
  const [error, setError] = useState(false);

  if (!itemId || error) {
    return (
      <div
        className="flex items-center justify-center rounded bg-card-elevated text-foreground-muted text-xs"
        style={{ width: size, height: size }}
        title={itemName}
      >
        ?
      </div>
    );
  }

  return (
    <Image
      src={`${RUNELITE_CDN}/${itemId}.png`}
      alt={itemName}
      width={size}
      height={size}
      className="object-contain"
      onError={() => setError(true)}
      unoptimized
    />
  );
}
```

- [ ] **Step 2: Create the PlayerLink component**

Create `C:/dev/clan-platform-web/src/components/player-link.tsx`:

```tsx
import Link from "next/link";

interface PlayerLinkProps {
  slug: string;
  rsn: string;
}

export function PlayerLink({ slug, rsn }: PlayerLinkProps) {
  return (
    <Link
      href={`/${slug}/players/${encodeURIComponent(rsn)}`}
      className="text-primary hover:text-primary-highlight transition-colors"
    >
      {rsn}
    </Link>
  );
}
```

- [ ] **Step 3: Create the TypeBadge component**

Create `C:/dev/clan-platform-web/src/components/type-badge.tsx`:

```tsx
const typeColors: Record<string, string> = {
  pet: "bg-rare/20 text-rare border-rare/40",
  "99": "bg-primary/20 text-primary border-primary/40",
  diary: "bg-success/20 text-success border-success/40",
  quest: "bg-info/20 text-info border-info/40",
  combat_achievement: "bg-destructive/20 text-destructive border-destructive/40",
  clue: "bg-orange-500/20 text-orange-400 border-orange-500/40",
};

const typeLabels: Record<string, string> = {
  pet: "Pet",
  "99": "99",
  diary: "Diary",
  quest: "Quest",
  combat_achievement: "Combat",
  clue: "Clue",
};

interface TypeBadgeProps {
  type: string;
}

export function TypeBadge({ type }: TypeBadgeProps) {
  const color = typeColors[type] ?? "bg-foreground-muted/20 text-foreground-muted border-foreground-muted/40";
  const label = typeLabels[type] ?? type;

  return (
    <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${color}`}>
      {label}
    </span>
  );
}
```

- [ ] **Step 4: Create the DropRow component**

Create `C:/dev/clan-platform-web/src/components/drop-row.tsx`:

```tsx
import { ItemIcon } from "./item-icon";
import { PlayerLink } from "./player-link";
import { formatValue, formatRelativeTime } from "@/lib/format";
import type { Drop } from "@/lib/types";

interface DropRowProps {
  drop: Drop;
  slug: string;
}

export function DropRow({ drop, slug }: DropRowProps) {
  const isHighValue = drop.value >= 1_000_000;

  return (
    <div className={`flex items-center gap-3 rounded-lg border p-3 ${isHighValue ? "border-primary/40 bg-primary/5" : "border-border bg-card"}`}>
      <ItemIcon itemId={drop.itemId} itemName={drop.itemName} size={36} />
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className={`font-medium truncate ${isHighValue ? "text-primary" : "text-foreground"}`}>
            {drop.itemName}
          </span>
          {drop.value > 0 && (
            <span className="text-foreground-muted text-sm">({formatValue(drop.value)})</span>
          )}
        </div>
        <div className="text-sm text-foreground-muted">
          <PlayerLink slug={slug} rsn={drop.rsn} /> from {drop.monsterName}
          {drop.killCount && <span> (KC: {drop.killCount.toLocaleString()})</span>}
        </div>
      </div>
      <span className="text-xs text-foreground-muted whitespace-nowrap">
        {formatRelativeTime(drop.createdAt)}
      </span>
    </div>
  );
}
```

- [ ] **Step 5: Create the LeaderboardTable component**

Create `C:/dev/clan-platform-web/src/components/leaderboard-table.tsx`:

```tsx
import { PlayerLink } from "./player-link";

interface LeaderboardRow {
  rsn: string;
  value: string | number;
}

interface LeaderboardTableProps {
  slug: string;
  rows: LeaderboardRow[];
  valueLabel: string;
}

export function LeaderboardTable({ slug, rows, valueLabel }: LeaderboardTableProps) {
  if (rows.length === 0) {
    return <p className="text-foreground-muted text-center py-8">No entries yet.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr className="border-b border-border text-left">
            <th className="py-2 px-3 text-primary font-heading text-sm w-12">#</th>
            <th className="py-2 px-3 text-primary font-heading text-sm">Player</th>
            <th className="py-2 px-3 text-primary font-heading text-sm text-right">{valueLabel}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={row.rsn} className="border-b border-border/50 hover:bg-card-elevated transition-colors">
              <td className="py-2 px-3 text-foreground-muted">{i + 1}</td>
              <td className="py-2 px-3"><PlayerLink slug={slug} rsn={row.rsn} /></td>
              <td className="py-2 px-3 text-right text-foreground">{row.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 6: Create the StatBar component**

Create `C:/dev/clan-platform-web/src/components/stat-bar.tsx`:

```tsx
interface Stat {
  label: string;
  value: string | number;
}

interface StatBarProps {
  stats: Stat[];
}

export function StatBar({ stats }: StatBarProps) {
  return (
    <div className="flex flex-wrap gap-6 rounded-lg border border-border bg-card p-4">
      {stats.map((stat) => (
        <div key={stat.label} className="text-center">
          <div className="text-2xl font-bold text-primary">{stat.value}</div>
          <div className="text-xs text-foreground-muted uppercase tracking-wider">{stat.label}</div>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 7: Configure Next.js for RuneLite CDN images**

Update `C:/dev/clan-platform-web/next.config.ts` — add `remotePatterns` for the RuneLite CDN:

```typescript
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "static.runelite.net",
        pathname: "/cache/item/icon/**",
      },
    ],
  },
};

export default nextConfig;
```

- [ ] **Step 8: Verify build**

```bash
cd C:/dev/clan-platform-web
npm run build
```

Expected: builds without errors.

- [ ] **Step 9: Commit**

```bash
cd C:/dev/clan-platform-web
git add .
git commit -m "feat: shared components — ItemIcon, DropRow, LeaderboardTable, TypeBadge, StatBar"
```

---

### Task 3: Clan Layout & Navigation

**Files:**
- Create: `src/components/nav.tsx`
- Create: `src/app/[slug]/layout.tsx`
- Create: `src/app/[slug]/not-found.tsx`

- [ ] **Step 1: Create the Nav component**

Create `C:/dev/clan-platform-web/src/components/nav.tsx`:

```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

interface NavProps {
  slug: string;
  clanName: string;
}

const navLinks = [
  { href: "", label: "Home" },
  { href: "/drops", label: "Drops" },
  { href: "/collection-log", label: "Collection Log" },
  { href: "/hiscores", label: "Hiscores" },
  { href: "/players", label: "Players" },
  { href: "/achievements", label: "Achievements" },
  { href: "/events", label: "Events" },
  { href: "/bingo", label: "Bingo" },
];

export function Nav({ slug, clanName }: NavProps) {
  const pathname = usePathname();

  function isActive(href: string) {
    const full = `/${slug}${href}`;
    if (href === "") return pathname === `/${slug}`;
    return pathname.startsWith(full);
  }

  return (
    <header className="sticky top-0 z-50 border-b border-border bg-background/95 backdrop-blur">
      <div className="mx-auto max-w-[1200px] flex items-center justify-between px-4 h-14">
        <Link href={`/${slug}`} className="font-heading text-xl text-primary hover:text-primary-highlight transition-colors">
          {clanName}
        </Link>

        <nav className="hidden md:flex items-center gap-1">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={`/${slug}${link.href}`}
              className={`px-3 py-1.5 rounded-md text-sm transition-colors ${
                isActive(link.href)
                  ? "bg-primary/10 text-primary"
                  : "text-foreground-muted hover:text-foreground hover:bg-card-elevated"
              }`}
            >
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <Link
            href={`/${slug}/settings`}
            className="text-sm text-foreground-muted hover:text-foreground transition-colors"
          >
            Login
          </Link>
        </div>
      </div>
    </header>
  );
}
```

- [ ] **Step 2: Create the clan layout**

Create `C:/dev/clan-platform-web/src/app/[slug]/layout.tsx`:

```tsx
import { notFound } from "next/navigation";
import { Nav } from "@/components/nav";
import { API_URL } from "@/lib/constants";

async function getClan(slug: string) {
  try {
    const res = await fetch(`${API_URL}/clans/${slug}/players`, { next: { revalidate: 60 } });
    if (!res.ok) return null;
    return { slug, name: slug.charAt(0).toUpperCase() + slug.slice(1) };
  } catch {
    return null;
  }
}

export default async function ClanLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const clan = await getClan(slug);
  if (!clan) notFound();

  return (
    <div className="min-h-screen">
      <Nav slug={slug} clanName={clan.name} />
      <main className="mx-auto max-w-[1200px] px-4 py-6">
        {children}
      </main>
    </div>
  );
}
```

- [ ] **Step 3: Create clan 404 page**

Create `C:/dev/clan-platform-web/src/app/[slug]/not-found.tsx`:

```tsx
import Link from "next/link";

export default function ClanNotFound() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center">
      <h1 className="font-heading text-4xl text-primary mb-4">Clan Not Found</h1>
      <p className="text-foreground-muted mb-6">This clan doesn't exist on the platform.</p>
      <Link href="/" className="text-primary hover:text-primary-highlight underline">
        Back to home
      </Link>
    </div>
  );
}
```

- [ ] **Step 4: Verify build**

```bash
cd C:/dev/clan-platform-web
npm run build
```

- [ ] **Step 5: Commit**

```bash
cd C:/dev/clan-platform-web
git add .
git commit -m "feat: clan layout with sticky navigation and 404 handling"
```

---

### Task 4: Clan Home Page

**Files:**
- Create: `src/components/drop-feed.tsx`
- Create: `src/app/[slug]/page.tsx`

- [ ] **Step 1: Create the DropFeed client component**

Create `C:/dev/clan-platform-web/src/components/drop-feed.tsx`:

```tsx
"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClientFetch } from "@/lib/api-client";
import { DropRow } from "./drop-row";
import type { Drop } from "@/lib/types";

interface DropFeedProps {
  slug: string;
  initialDrops: Drop[];
}

export function DropFeed({ slug, initialDrops }: DropFeedProps) {
  const { data } = useQuery({
    queryKey: ["drops", slug],
    queryFn: () => apiClientFetch<{ drops: Drop[] }>(`/clans/${slug}/drops?limit=20`),
    initialData: { drops: initialDrops },
    refetchInterval: 30_000,
  });

  const drops = data.drops;

  if (drops.length === 0) {
    return <p className="text-foreground-muted text-center py-8">No drops logged yet.</p>;
  }

  return (
    <div className="flex flex-col gap-2">
      {drops.map((drop) => (
        <DropRow key={drop.id} drop={drop} slug={slug} />
      ))}
    </div>
  );
}
```

- [ ] **Step 2: Create the clan home page**

Create `C:/dev/clan-platform-web/src/app/[slug]/page.tsx`:

```tsx
import { apiFetch } from "@/lib/api";
import { StatBar } from "@/components/stat-bar";
import { DropFeed } from "@/components/drop-feed";
import type { Drop, Player } from "@/lib/types";

interface Props {
  params: Promise<{ slug: string }>;
}

export default async function ClanHomePage({ params }: Props) {
  const { slug } = await params;

  let drops: Drop[] = [];
  let players: Player[] = [];

  try {
    const [dropsRes, playersRes] = await Promise.all([
      apiFetch<{ drops: Drop[] }>(`/clans/${slug}/drops?limit=20`),
      apiFetch<{ players: Player[] }>(`/clans/${slug}/players`),
    ]);
    drops = dropsRes.drops;
    players = playersRes.players;
  } catch {
    // API may be down — render with empty data
  }

  return (
    <div className="space-y-6">
      <h1 className="font-heading text-3xl text-primary">Welcome</h1>

      <StatBar stats={[
        { label: "Members", value: players.length },
        { label: "Drops Logged", value: drops.length > 0 ? "20+" : "0" },
      ]} />

      <section>
        <h2 className="font-heading text-xl text-primary mb-3">Recent Drops</h2>
        <DropFeed slug={slug} initialDrops={drops} />
      </section>
    </div>
  );
}
```

- [ ] **Step 3: Verify with dev server**

```bash
cd C:/dev/clan-platform-web
npm run dev
```

Make sure the API is running (`cd C:/dev/clan-platform-api && npm run dev`), then visit `http://localhost:3000/solus`. Should see the nav bar, stats bar, and drop feed (empty if no drops in the DB, or populated if there are).

- [ ] **Step 4: Commit**

```bash
cd C:/dev/clan-platform-web
git add .
git commit -m "feat: clan home page with live drop feed and stat bar"
```

---

### Task 5: Drops Page

**Files:**
- Create: `src/app/[slug]/drops/page.tsx`

- [ ] **Step 1: Create the drops page with filters and infinite scroll**

Create `C:/dev/clan-platform-web/src/app/[slug]/drops/page.tsx`:

```tsx
"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useInfiniteQuery } from "@tanstack/react-query";
import { apiClientFetch } from "@/lib/api-client";
import { DropRow } from "@/components/drop-row";
import type { Drop } from "@/lib/types";

export default function DropsPage() {
  const { slug } = useParams<{ slug: string }>();
  const [player, setPlayer] = useState("");
  const [item, setItem] = useState("");
  const [monster, setMonster] = useState("");

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: ["drops", slug, player, item, monster],
    queryFn: async ({ pageParam = 0 }) => {
      const params = new URLSearchParams({ limit: "20", offset: String(pageParam) });
      if (player) params.set("player", player);
      if (item) params.set("item", item);
      if (monster) params.set("monster", monster);
      return apiClientFetch<{ drops: Drop[] }>(`/clans/${slug}/drops?${params}`);
    },
    getNextPageParam: (lastPage, allPages) => {
      if (lastPage.drops.length < 20) return undefined;
      return allPages.reduce((acc, p) => acc + p.drops.length, 0);
    },
    initialPageParam: 0,
  });

  const allDrops = data?.pages.flatMap((p) => p.drops) ?? [];

  return (
    <div className="space-y-4">
      <h1 className="font-heading text-3xl text-primary">Drop Log</h1>

      <div className="flex flex-wrap gap-3">
        <input
          type="text"
          placeholder="Filter by player..."
          value={player}
          onChange={(e) => setPlayer(e.target.value)}
          className="rounded-md border border-border bg-card px-3 py-2 text-sm text-foreground placeholder:text-foreground-muted focus:border-border-active focus:outline-none"
        />
        <input
          type="text"
          placeholder="Filter by item..."
          value={item}
          onChange={(e) => setItem(e.target.value)}
          className="rounded-md border border-border bg-card px-3 py-2 text-sm text-foreground placeholder:text-foreground-muted focus:border-border-active focus:outline-none"
        />
        <input
          type="text"
          placeholder="Filter by monster..."
          value={monster}
          onChange={(e) => setMonster(e.target.value)}
          className="rounded-md border border-border bg-card px-3 py-2 text-sm text-foreground placeholder:text-foreground-muted focus:border-border-active focus:outline-none"
        />
      </div>

      {allDrops.length === 0 ? (
        <p className="text-foreground-muted text-center py-8">No drops found.</p>
      ) : (
        <div className="flex flex-col gap-2">
          {allDrops.map((drop) => (
            <DropRow key={drop.id} drop={drop} slug={slug} />
          ))}
        </div>
      )}

      {hasNextPage && (
        <div className="flex justify-center pt-4">
          <button
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
            className="rounded-lg bg-primary px-6 py-2 text-background font-semibold hover:bg-primary-hover disabled:opacity-50 transition-colors"
          >
            {isFetchingNextPage ? "Loading..." : "Load More"}
          </button>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Verify**

```bash
cd C:/dev/clan-platform-web
npm run build
```

- [ ] **Step 3: Commit**

```bash
cd C:/dev/clan-platform-web
git add .
git commit -m "feat: drops page with filters and infinite scroll"
```

---

### Task 6: Collection Log Pages

**Files:**
- Create: `src/app/[slug]/collection-log/page.tsx`
- Create: `src/app/[slug]/collection-log/[rsn]/page.tsx`

- [ ] **Step 1: Create the collection log leaderboard page**

Create `C:/dev/clan-platform-web/src/app/[slug]/collection-log/page.tsx`:

```tsx
import { apiFetch } from "@/lib/api";
import { LeaderboardTable } from "@/components/leaderboard-table";
import type { ClogLeaderboardEntry } from "@/lib/types";

interface Props {
  params: Promise<{ slug: string }>;
}

export default async function CollectionLogPage({ params }: Props) {
  const { slug } = await params;

  let leaderboard: ClogLeaderboardEntry[] = [];
  try {
    const res = await apiFetch<{ leaderboard: ClogLeaderboardEntry[] }>(`/clans/${slug}/collection-log`);
    leaderboard = res.leaderboard;
  } catch {
    // API down
  }

  const rows = leaderboard.map((entry) => ({
    rsn: entry.rsn,
    value: entry.uniqueCount,
  }));

  return (
    <div className="space-y-4">
      <h1 className="font-heading text-3xl text-primary">Collection Log</h1>
      <div className="rounded-lg border border-border bg-card p-4">
        <LeaderboardTable slug={slug} rows={rows} valueLabel="Unique Items" />
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Create the player collection log page**

Create `C:/dev/clan-platform-web/src/app/[slug]/collection-log/[rsn]/page.tsx`:

```tsx
import { notFound } from "next/navigation";
import { apiFetch } from "@/lib/api";
import { ItemIcon } from "@/components/item-icon";
import { formatRelativeTime } from "@/lib/format";
import type { ClogEntry } from "@/lib/types";

interface Props {
  params: Promise<{ slug: string; rsn: string }>;
}

export default async function PlayerClogPage({ params }: Props) {
  const { slug, rsn } = await params;
  const decodedRsn = decodeURIComponent(rsn);

  let entries: ClogEntry[] = [];
  let total = 0;

  try {
    const res = await apiFetch<{ entries: ClogEntry[]; total: number }>(
      `/clans/${slug}/collection-log/${encodeURIComponent(decodedRsn)}`
    );
    entries = res.entries;
    total = res.total;
  } catch {
    notFound();
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="font-heading text-3xl text-primary">{decodedRsn}</h1>
        <p className="text-foreground-muted">{total} unique items</p>
      </div>

      {entries.length === 0 ? (
        <p className="text-foreground-muted text-center py-8">No collection log entries for {decodedRsn}.</p>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-3">
          {entries.map((entry) => (
            <div
              key={entry.id}
              className="flex flex-col items-center gap-1 rounded-lg border border-border bg-card p-3 hover:border-border-active transition-colors"
              title={`Obtained ${formatRelativeTime(entry.obtainedAt)}`}
            >
              <ItemIcon itemId={entry.itemId} itemName={entry.itemName} size={36} />
              <span className="text-xs text-foreground-muted text-center truncate w-full">
                {entry.itemName}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Verify build**

```bash
cd C:/dev/clan-platform-web
npm run build
```

- [ ] **Step 4: Commit**

```bash
cd C:/dev/clan-platform-web
git add .
git commit -m "feat: collection log leaderboard and player clog pages"
```

---

### Task 7: Hiscores Page

**Files:**
- Create: `src/app/[slug]/hiscores/page.tsx`

- [ ] **Step 1: Create the hiscores page**

Create `C:/dev/clan-platform-web/src/app/[slug]/hiscores/page.tsx`:

```tsx
"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiClientFetch } from "@/lib/api-client";
import { LeaderboardTable } from "@/components/leaderboard-table";
import { formatTime } from "@/lib/format";
import type { PbLeaderboardEntry, PbRanking } from "@/lib/types";

export default function HiscoresPage() {
  const { slug } = useParams<{ slug: string }>();
  const [selectedBoss, setSelectedBoss] = useState<string>("");
  const [teamSize, setTeamSize] = useState(1);

  // Fetch all PBs to get the list of bosses
  const { data: allPbs } = useQuery({
    queryKey: ["pbs", slug],
    queryFn: () => apiClientFetch<{ leaderboard: PbLeaderboardEntry[] }>(`/clans/${slug}/pbs`),
  });

  const bosses = [...new Set(allPbs?.leaderboard.map((e) => e.bossKey) ?? [])].sort();
  const activeBoss = selectedBoss || bosses[0] || "";

  // Fetch rankings for selected boss
  const { data: rankings } = useQuery({
    queryKey: ["pbs", slug, activeBoss, teamSize],
    queryFn: () => apiClientFetch<{ rankings: PbRanking[] }>(`/clans/${slug}/pbs/${activeBoss}?teamSize=${teamSize}`),
    enabled: !!activeBoss,
  });

  const rows = (rankings?.rankings ?? []).map((r) => ({
    rsn: r.rsn,
    value: formatTime(r.timeMs),
  }));

  return (
    <div className="space-y-4">
      <h1 className="font-heading text-3xl text-primary">Hiscores</h1>

      <div className="flex flex-wrap gap-3">
        <select
          value={activeBoss}
          onChange={(e) => setSelectedBoss(e.target.value)}
          className="rounded-md border border-border bg-card px-3 py-2 text-sm text-foreground focus:border-border-active focus:outline-none"
        >
          {bosses.length === 0 && <option value="">No bosses</option>}
          {bosses.map((boss) => (
            <option key={boss} value={boss}>
              {boss.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase())}
            </option>
          ))}
        </select>

        <select
          value={teamSize}
          onChange={(e) => setTeamSize(Number(e.target.value))}
          className="rounded-md border border-border bg-card px-3 py-2 text-sm text-foreground focus:border-border-active focus:outline-none"
        >
          <option value={1}>Solo</option>
          <option value={2}>Duo</option>
          <option value={3}>Trio</option>
          <option value={4}>4-man</option>
          <option value={5}>5-man</option>
        </select>
      </div>

      <div className="rounded-lg border border-border bg-card p-4">
        <LeaderboardTable slug={slug} rows={rows} valueLabel="Best Time" />
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Verify build**

```bash
cd C:/dev/clan-platform-web
npm run build
```

- [ ] **Step 3: Commit**

```bash
cd C:/dev/clan-platform-web
git add .
git commit -m "feat: hiscores page with boss selector and team size filter"
```

---

### Task 8: Players & Profile Pages

**Files:**
- Create: `src/app/[slug]/players/page.tsx`
- Create: `src/app/[slug]/players/[rsn]/page.tsx`

- [ ] **Step 1: Create the players roster page**

Create `C:/dev/clan-platform-web/src/app/[slug]/players/page.tsx`:

```tsx
"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiClientFetch } from "@/lib/api-client";
import { PlayerLink } from "@/components/player-link";
import type { Player } from "@/lib/types";

export default function PlayersPage() {
  const { slug } = useParams<{ slug: string }>();
  const [search, setSearch] = useState("");

  const { data } = useQuery({
    queryKey: ["players", slug],
    queryFn: () => apiClientFetch<{ players: Player[] }>(`/clans/${slug}/players`),
  });

  const players = (data?.players ?? []).filter((p) =>
    p.rsn.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-4">
      <h1 className="font-heading text-3xl text-primary">Clan Roster</h1>

      <input
        type="text"
        placeholder="Search players..."
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        className="rounded-md border border-border bg-card px-3 py-2 text-sm text-foreground placeholder:text-foreground-muted focus:border-border-active focus:outline-none w-full max-w-xs"
      />

      <div className="rounded-lg border border-border bg-card overflow-hidden">
        <table className="w-full">
          <thead>
            <tr className="border-b border-border text-left">
              <th className="py-2 px-4 text-primary font-heading text-sm">RSN</th>
              <th className="py-2 px-4 text-primary font-heading text-sm">Joined</th>
              <th className="py-2 px-4 text-primary font-heading text-sm">Verified</th>
            </tr>
          </thead>
          <tbody>
            {players.map((player) => (
              <tr key={player.id} className="border-b border-border/50 hover:bg-card-elevated transition-colors">
                <td className="py-2 px-4">
                  <PlayerLink slug={slug} rsn={player.rsn} />
                </td>
                <td className="py-2 px-4 text-foreground-muted text-sm">
                  {new Date(player.joinedAt).toLocaleDateString()}
                </td>
                <td className="py-2 px-4">
                  {player.verified ? (
                    <span className="text-success text-sm">Verified</span>
                  ) : (
                    <span className="text-foreground-muted text-sm">-</span>
                  )}
                </td>
              </tr>
            ))}
            {players.length === 0 && (
              <tr>
                <td colSpan={3} className="py-8 text-center text-foreground-muted">
                  {search ? "No players match your search." : "No players yet."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Create the player profile page**

Create `C:/dev/clan-platform-web/src/app/[slug]/players/[rsn]/page.tsx`:

```tsx
import { notFound } from "next/navigation";
import { apiFetch } from "@/lib/api";
import { ItemIcon } from "@/components/item-icon";
import { TypeBadge } from "@/components/type-badge";
import { formatValue, formatTime, formatRelativeTime } from "@/lib/format";
import type { PlayerProfile } from "@/lib/types";
import Link from "next/link";

interface Props {
  params: Promise<{ slug: string; rsn: string }>;
}

export default async function PlayerProfilePage({ params }: Props) {
  const { slug, rsn } = await params;
  const decodedRsn = decodeURIComponent(rsn);

  let profile: PlayerProfile;
  try {
    profile = await apiFetch<PlayerProfile>(`/clans/${slug}/players/${encodeURIComponent(decodedRsn)}`);
  } catch {
    notFound();
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-heading text-3xl text-primary">{profile.player.rsn}</h1>
        <div className="flex items-center gap-3 text-sm text-foreground-muted">
          <span>Joined {new Date(profile.player.joinedAt).toLocaleDateString()}</span>
          {profile.player.verified && <span className="text-success">Verified</span>}
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Recent Drops */}
        <div className="rounded-lg border border-border bg-card p-4">
          <h2 className="font-heading text-lg text-primary mb-3">Recent Drops</h2>
          {profile.recentDrops.length === 0 ? (
            <p className="text-foreground-muted text-sm">No drops yet.</p>
          ) : (
            <div className="space-y-2">
              {profile.recentDrops.map((drop, i) => (
                <div key={i} className="flex items-center justify-between text-sm">
                  <span className="text-foreground">{drop.itemName}</span>
                  <span className="text-foreground-muted">{formatValue(drop.value)}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Personal Bests */}
        <div className="rounded-lg border border-border bg-card p-4">
          <h2 className="font-heading text-lg text-primary mb-3">Personal Bests</h2>
          {profile.personalBests.length === 0 ? (
            <p className="text-foreground-muted text-sm">No PBs yet.</p>
          ) : (
            <div className="space-y-2">
              {profile.personalBests.map((pb, i) => (
                <div key={i} className="flex items-center justify-between text-sm">
                  <span className="text-foreground">
                    {pb.bossKey.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase())}
                    {pb.teamSize > 1 && <span className="text-foreground-muted ml-1">({pb.teamSize}-man)</span>}
                  </span>
                  <span className="font-mono text-primary">{formatTime(pb.timeMs)}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Collection Log */}
        <div className="rounded-lg border border-border bg-card p-4">
          <h2 className="font-heading text-lg text-primary mb-3">Collection Log</h2>
          <div className="text-3xl font-bold text-primary">{profile.collectionLogCount}</div>
          <p className="text-foreground-muted text-sm">unique items</p>
          {profile.collectionLogCount > 0 && (
            <Link
              href={`/${slug}/collection-log/${encodeURIComponent(decodedRsn)}`}
              className="text-sm text-primary hover:text-primary-highlight mt-2 inline-block"
            >
              View full log
            </Link>
          )}
        </div>

        {/* Recent Achievements */}
        <div className="rounded-lg border border-border bg-card p-4">
          <h2 className="font-heading text-lg text-primary mb-3">Recent Achievements</h2>
          {profile.recentAchievements.length === 0 ? (
            <p className="text-foreground-muted text-sm">No achievements yet.</p>
          ) : (
            <div className="space-y-2">
              {profile.recentAchievements.map((ach, i) => (
                <div key={i} className="flex items-center gap-2 text-sm">
                  <TypeBadge type={ach.type} />
                  <span className="text-foreground">{ach.detail}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Verify build**

```bash
cd C:/dev/clan-platform-web
npm run build
```

- [ ] **Step 4: Commit**

```bash
cd C:/dev/clan-platform-web
git add .
git commit -m "feat: players roster and player profile pages"
```

---

### Task 9: Achievements Page

**Files:**
- Create: `src/app/[slug]/achievements/page.tsx`

- [ ] **Step 1: Create the achievements page**

Create `C:/dev/clan-platform-web/src/app/[slug]/achievements/page.tsx`:

```tsx
"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiClientFetch } from "@/lib/api-client";
import { TypeBadge } from "@/components/type-badge";
import { PlayerLink } from "@/components/player-link";
import { formatRelativeTime } from "@/lib/format";
import type { Achievement } from "@/lib/types";

const types = [
  { value: "", label: "All" },
  { value: "pet", label: "Pet" },
  { value: "99", label: "99" },
  { value: "diary", label: "Diary" },
  { value: "quest", label: "Quest" },
  { value: "combat_achievement", label: "Combat" },
  { value: "clue", label: "Clue" },
];

export default function AchievementsPage() {
  const { slug } = useParams<{ slug: string }>();
  const [typeFilter, setTypeFilter] = useState("");

  const params = new URLSearchParams({ limit: "50" });
  if (typeFilter) params.set("type", typeFilter);

  const { data } = useQuery({
    queryKey: ["achievements", slug, typeFilter],
    queryFn: () => apiClientFetch<{ achievements: Achievement[] }>(`/clans/${slug}/achievements?${params}`),
  });

  const achievements = data?.achievements ?? [];

  return (
    <div className="space-y-4">
      <h1 className="font-heading text-3xl text-primary">Achievements</h1>

      <div className="flex flex-wrap gap-2">
        {types.map((t) => (
          <button
            key={t.value}
            onClick={() => setTypeFilter(t.value)}
            className={`rounded-full px-3 py-1 text-sm transition-colors ${
              typeFilter === t.value
                ? "bg-primary text-background"
                : "border border-border text-foreground-muted hover:text-foreground hover:border-border-active"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {achievements.length === 0 ? (
        <p className="text-foreground-muted text-center py-8">No achievements found.</p>
      ) : (
        <div className="space-y-2">
          {achievements.map((ach) => (
            <div key={ach.id} className="flex items-center gap-3 rounded-lg border border-border bg-card p-3">
              <TypeBadge type={ach.type} />
              <div className="flex-1">
                <span className="text-foreground font-medium">{ach.detail}</span>
                <span className="text-foreground-muted text-sm ml-2">
                  by <PlayerLink slug={slug} rsn={ach.rsn} />
                </span>
              </div>
              <span className="text-xs text-foreground-muted whitespace-nowrap">
                {formatRelativeTime(ach.createdAt)}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Verify build**

```bash
cd C:/dev/clan-platform-web
npm run build
```

- [ ] **Step 3: Commit**

```bash
cd C:/dev/clan-platform-web
git add .
git commit -m "feat: achievements page with type filter"
```

---

### Task 10: Events & Bingo Placeholder Pages

**Files:**
- Create: `src/app/[slug]/events/page.tsx`
- Create: `src/app/[slug]/bingo/page.tsx`
- Create: `src/components/event-card.tsx`

- [ ] **Step 1: Create the EventCard component**

Create `C:/dev/clan-platform-web/src/components/event-card.tsx`:

```tsx
interface EventCardProps {
  type: string;
  name: string;
  endTime: string;
}

export function EventCard({ type, name, endTime }: EventCardProps) {
  const end = new Date(endTime);
  const now = new Date();
  const remaining = end.getTime() - now.getTime();
  const isActive = remaining > 0;

  const days = Math.floor(remaining / (1000 * 60 * 60 * 24));
  const hours = Math.floor((remaining % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));

  const typeColors: Record<string, string> = {
    boss: "border-destructive/40",
    skill: "border-success/40",
    gamer: "border-rare/40",
    clue: "border-orange-500/40",
  };

  return (
    <div className={`rounded-lg border-2 bg-card p-4 ${typeColors[type] ?? "border-border"}`}>
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs uppercase tracking-wider text-foreground-muted">{type} of the Week</span>
        {isActive && (
          <span className="text-xs text-foreground-muted">
            {days > 0 ? `${days}d ${hours}h remaining` : `${hours}h remaining`}
          </span>
        )}
      </div>
      <h3 className="font-heading text-xl text-primary">{name}</h3>
    </div>
  );
}
```

- [ ] **Step 2: Create the events page**

Create `C:/dev/clan-platform-web/src/app/[slug]/events/page.tsx`:

```tsx
export default function EventsPage() {
  return (
    <div className="space-y-4">
      <h1 className="font-heading text-3xl text-primary">Events</h1>
      <div className="rounded-lg border border-border bg-card p-8 text-center">
        <p className="text-foreground-muted">No events yet.</p>
        <p className="text-foreground-muted text-sm mt-1">
          Events will appear here once the events system is live.
        </p>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Create the bingo page**

Create `C:/dev/clan-platform-web/src/app/[slug]/bingo/page.tsx`:

```tsx
export default function BingoPage() {
  return (
    <div className="space-y-4">
      <h1 className="font-heading text-3xl text-primary">Bingo</h1>
      <div className="rounded-lg border border-border bg-card p-8 text-center">
        <p className="text-foreground-muted">No active bingo event.</p>
        <p className="text-foreground-muted text-sm mt-1">
          Bingo boards will appear here once the bingo system is live.
        </p>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Verify build**

```bash
cd C:/dev/clan-platform-web
npm run build
```

- [ ] **Step 5: Commit**

```bash
cd C:/dev/clan-platform-web
git add .
git commit -m "feat: events and bingo placeholder pages with EventCard component"
```

---

### Task 11: Discord OAuth Authentication

**Files:**
- Create: `src/lib/auth.ts`
- Create: `src/app/auth/discord/callback/route.ts`
- Create: `src/app/api/auth/me/route.ts`
- Create: `src/app/api/auth/logout/route.ts`
- Create: `src/providers/auth-provider.tsx`
- Create: `src/hooks/use-auth.ts`
- Create: `src/middleware.ts`

- [ ] **Step 1: Create auth cookie helpers**

Create `C:/dev/clan-platform-web/src/lib/auth.ts`:

```typescript
import { cookies } from "next/headers";
import { jwtVerify } from "jose";
import { JWT_SECRET } from "./constants";

const ACCESS_COOKIE = "clan_access_token";
const REFRESH_COOKIE = "clan_refresh_token";

const secret = new TextEncoder().encode(JWT_SECRET);

export async function getTokens() {
  const cookieStore = await cookies();
  return {
    accessToken: cookieStore.get(ACCESS_COOKIE)?.value,
    refreshToken: cookieStore.get(REFRESH_COOKIE)?.value,
  };
}

export async function setTokenCookies(accessToken: string, refreshToken: string) {
  const cookieStore = await cookies();

  cookieStore.set(ACCESS_COOKIE, accessToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60, // 1 hour
  });

  cookieStore.set(REFRESH_COOKIE, refreshToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60 * 24 * 30, // 30 days
  });
}

export async function clearTokenCookies() {
  const cookieStore = await cookies();
  cookieStore.delete(ACCESS_COOKIE);
  cookieStore.delete(REFRESH_COOKIE);
}

export async function verifyAccessToken(token: string) {
  try {
    const { payload } = await jwtVerify(token, secret);
    return payload as { sub: string; discordId: string; username: string };
  } catch {
    return null;
  }
}

export async function hasValidAuth(): Promise<boolean> {
  const { accessToken, refreshToken } = await getTokens();
  if (accessToken) {
    const payload = await verifyAccessToken(accessToken);
    if (payload) return true;
  }
  return !!refreshToken;
}
```

- [ ] **Step 2: Create the Discord OAuth callback route handler**

Create `C:/dev/clan-platform-web/src/app/auth/discord/callback/route.ts`:

```typescript
import { NextRequest, NextResponse } from "next/server";
import { API_URL, DISCORD_CLIENT_ID, DISCORD_REDIRECT_URI } from "@/lib/constants";
import { setTokenCookies } from "@/lib/auth";

export async function GET(request: NextRequest) {
  const code = request.nextUrl.searchParams.get("code");
  const returnTo = request.nextUrl.searchParams.get("state") ?? "/";

  if (!code) {
    return NextResponse.redirect(new URL("/", request.url));
  }

  try {
    const res = await fetch(`${API_URL}/auth/discord/callback`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ code }),
    });

    if (!res.ok) {
      return NextResponse.redirect(new URL("/?error=auth_failed", request.url));
    }

    const data = await res.json();
    await setTokenCookies(data.accessToken, data.refreshToken);

    return NextResponse.redirect(new URL(returnTo, request.url));
  } catch {
    return NextResponse.redirect(new URL("/?error=auth_failed", request.url));
  }
}
```

- [ ] **Step 3: Create the `/api/auth/me` route handler**

Create `C:/dev/clan-platform-web/src/app/api/auth/me/route.ts`:

```typescript
import { NextResponse } from "next/server";
import { getTokens, verifyAccessToken, setTokenCookies } from "@/lib/auth";
import { API_URL } from "@/lib/constants";

export async function GET() {
  const { accessToken, refreshToken } = await getTokens();

  if (accessToken) {
    const payload = await verifyAccessToken(accessToken);
    if (payload) {
      return NextResponse.json({
        id: payload.sub,
        username: payload.username,
        discordId: payload.discordId,
      });
    }
  }

  // Try refresh
  if (refreshToken) {
    try {
      const res = await fetch(`${API_URL}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });

      if (res.ok) {
        const data = await res.json();
        await setTokenCookies(data.accessToken, data.refreshToken);

        const payload = await verifyAccessToken(data.accessToken);
        if (payload) {
          return NextResponse.json({
            id: payload.sub,
            username: payload.username,
            discordId: payload.discordId,
          });
        }
      }
    } catch {
      // Refresh failed
    }
  }

  return NextResponse.json(null, { status: 401 });
}
```

- [ ] **Step 4: Create the logout route handler**

Create `C:/dev/clan-platform-web/src/app/api/auth/logout/route.ts`:

```typescript
import { NextResponse } from "next/server";
import { clearTokenCookies } from "@/lib/auth";

export async function POST() {
  await clearTokenCookies();
  return NextResponse.json({ ok: true });
}
```

- [ ] **Step 5: Create auth provider and hook**

Create `C:/dev/clan-platform-web/src/providers/auth-provider.tsx`:

```tsx
"use client";

import { createContext, useEffect, useState, type ReactNode } from "react";
import type { AuthUser } from "@/lib/types";

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue>({
  user: null,
  loading: true,
  logout: async () => {},
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch("/api/auth/me")
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => setUser(data))
      .finally(() => setLoading(false));
  }, []);

  async function logout() {
    await fetch("/api/auth/logout", { method: "POST" });
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, loading, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
```

Create `C:/dev/clan-platform-web/src/hooks/use-auth.ts`:

```typescript
"use client";

import { useContext } from "react";
import { AuthContext } from "@/providers/auth-provider";

export function useAuth() {
  return useContext(AuthContext);
}
```

- [ ] **Step 6: Add AuthProvider to root layout**

Update `C:/dev/clan-platform-web/src/app/layout.tsx` — wrap children with AuthProvider inside QueryProvider:

```tsx
import type { Metadata } from "next";
import { Inter, Cinzel } from "next/font/google";
import { QueryProvider } from "@/providers/query-provider";
import { AuthProvider } from "@/providers/auth-provider";
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });
const cinzel = Cinzel({ subsets: ["latin"], variable: "--font-cinzel" });

export const metadata: Metadata = {
  title: "Clan Platform",
  description: "OSRS Clan Management Platform",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="dark">
      <body className={`${inter.variable} ${cinzel.variable} font-sans antialiased`}>
        <QueryProvider>
          <AuthProvider>
            {children}
          </AuthProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
```

- [ ] **Step 7: Update Nav to show login state**

Update the login area in `C:/dev/clan-platform-web/src/components/nav.tsx` — replace the static "Login" link with dynamic auth state:

```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/hooks/use-auth";
import { DISCORD_CLIENT_ID, DISCORD_REDIRECT_URI } from "@/lib/constants";

interface NavProps {
  slug: string;
  clanName: string;
}

const navLinks = [
  { href: "", label: "Home" },
  { href: "/drops", label: "Drops" },
  { href: "/collection-log", label: "Collection Log" },
  { href: "/hiscores", label: "Hiscores" },
  { href: "/players", label: "Players" },
  { href: "/achievements", label: "Achievements" },
  { href: "/events", label: "Events" },
  { href: "/bingo", label: "Bingo" },
];

export function Nav({ slug, clanName }: NavProps) {
  const pathname = usePathname();
  const { user, loading, logout } = useAuth();

  function isActive(href: string) {
    const full = `/${slug}${href}`;
    if (href === "") return pathname === `/${slug}`;
    return pathname.startsWith(full);
  }

  const discordAuthUrl = `https://discord.com/api/oauth2/authorize?client_id=${DISCORD_CLIENT_ID}&redirect_uri=${encodeURIComponent(DISCORD_REDIRECT_URI)}&response_type=code&scope=identify&state=${encodeURIComponent(pathname)}`;

  return (
    <header className="sticky top-0 z-50 border-b border-border bg-background/95 backdrop-blur">
      <div className="mx-auto max-w-[1200px] flex items-center justify-between px-4 h-14">
        <Link href={`/${slug}`} className="font-heading text-xl text-primary hover:text-primary-highlight transition-colors">
          {clanName}
        </Link>

        <nav className="hidden md:flex items-center gap-1">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={`/${slug}${link.href}`}
              className={`px-3 py-1.5 rounded-md text-sm transition-colors ${
                isActive(link.href)
                  ? "bg-primary/10 text-primary"
                  : "text-foreground-muted hover:text-foreground hover:bg-card-elevated"
              }`}
            >
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          {loading ? null : user ? (
            <div className="flex items-center gap-3">
              <span className="text-sm text-foreground">{user.username}</span>
              <button
                onClick={logout}
                className="text-sm text-foreground-muted hover:text-foreground transition-colors"
              >
                Logout
              </button>
            </div>
          ) : (
            <a
              href={discordAuthUrl}
              className="rounded-md bg-[#5865F2] px-3 py-1.5 text-sm text-white font-medium hover:bg-[#4752C4] transition-colors"
            >
              Login with Discord
            </a>
          )}
        </div>
      </div>
    </header>
  );
}
```

- [ ] **Step 8: Create middleware for protected routes**

Create `C:/dev/clan-platform-web/src/middleware.ts`:

```typescript
import { NextRequest, NextResponse } from "next/server";

export function middleware(request: NextRequest) {
  const accessToken = request.cookies.get("clan_access_token")?.value;
  const refreshToken = request.cookies.get("clan_refresh_token")?.value;

  if (!accessToken && !refreshToken) {
    const loginUrl = new URL("/", request.url);
    loginUrl.searchParams.set("error", "login_required");
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/:slug/settings", "/:slug/admin"],
};
```

- [ ] **Step 9: Verify build**

```bash
cd C:/dev/clan-platform-web
npm run build
```

- [ ] **Step 10: Commit**

```bash
cd C:/dev/clan-platform-web
git add .
git commit -m "feat: Discord OAuth authentication with JWT cookies and middleware"
```

---

### Task 12: Settings & Admin Placeholder Pages

**Files:**
- Create: `src/app/[slug]/settings/page.tsx`
- Create: `src/app/[slug]/admin/page.tsx`

- [ ] **Step 1: Create the settings page**

Create `C:/dev/clan-platform-web/src/app/[slug]/settings/page.tsx`:

```tsx
"use client";

import { useAuth } from "@/hooks/use-auth";

export default function SettingsPage() {
  const { user, loading } = useAuth();

  if (loading) {
    return <p className="text-foreground-muted">Loading...</p>;
  }

  if (!user) {
    return <p className="text-foreground-muted">You must be logged in to view settings.</p>;
  }

  return (
    <div className="space-y-6">
      <h1 className="font-heading text-3xl text-primary">Settings</h1>

      <div className="rounded-lg border border-border bg-card p-6 max-w-md">
        <h2 className="font-heading text-lg text-primary mb-4">Your Account</h2>
        <div className="space-y-3 text-sm">
          <div>
            <span className="text-foreground-muted">Discord:</span>{" "}
            <span className="text-foreground">{user.username}</span>
          </div>
          <div>
            <span className="text-foreground-muted">Linked RSN:</span>{" "}
            <span className="text-foreground-muted italic">Not linked yet</span>
          </div>
        </div>
        <p className="text-foreground-muted text-xs mt-4">
          RSN linking will be available once the verification system is live.
        </p>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Create the admin page**

Create `C:/dev/clan-platform-web/src/app/[slug]/admin/page.tsx`:

```tsx
"use client";

import { useState } from "react";
import { useAuth } from "@/hooks/use-auth";

const tabs = [
  { id: "events", label: "Events", phase: 4 },
  { id: "bingo", label: "Bingo", phase: 4 },
  { id: "whitelist", label: "Whitelist", phase: 5 },
  { id: "roles", label: "Roles", phase: 5 },
  { id: "webhooks", label: "Webhooks", phase: 5 },
  { id: "roster", label: "Roster", phase: 5 },
];

export default function AdminPage() {
  const { user, loading } = useAuth();
  const [activeTab, setActiveTab] = useState("events");

  if (loading) {
    return <p className="text-foreground-muted">Loading...</p>;
  }

  if (!user) {
    return <p className="text-foreground-muted">You must be logged in to access the admin dashboard.</p>;
  }

  const currentTab = tabs.find((t) => t.id === activeTab)!;

  return (
    <div className="space-y-6">
      <h1 className="font-heading text-3xl text-primary">Admin Dashboard</h1>

      <div className="flex gap-1 border-b border-border">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 text-sm transition-colors border-b-2 -mb-px ${
              activeTab === tab.id
                ? "border-primary text-primary"
                : "border-transparent text-foreground-muted hover:text-foreground"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="rounded-lg border border-border bg-card p-8 text-center">
        <p className="text-foreground-muted">
          {currentTab.label} management coming in Phase {currentTab.phase}.
        </p>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Verify build**

```bash
cd C:/dev/clan-platform-web
npm run build
```

- [ ] **Step 4: Commit**

```bash
cd C:/dev/clan-platform-web
git add .
git commit -m "feat: settings and admin placeholder pages"
```

---

### Task 13: Final Integration Verification

**Files:** None (testing only)

- [ ] **Step 1: Start both servers**

Terminal 1:
```bash
cd C:/dev/clan-platform-api && npm run dev
```

Terminal 2:
```bash
cd C:/dev/clan-platform-web && npm run dev
```

- [ ] **Step 2: Verify all pages load**

Visit each URL and confirm it renders without errors:

| URL | Expected |
|-----|----------|
| `http://localhost:3000` | Landing page with "Clan Platform" heading and "Visit Solus" link |
| `http://localhost:3000/solus` | Clan home with nav bar, stats, drop feed (may be empty) |
| `http://localhost:3000/solus/drops` | Drop log with filter inputs |
| `http://localhost:3000/solus/collection-log` | Collection log leaderboard |
| `http://localhost:3000/solus/hiscores` | Hiscores with boss selector |
| `http://localhost:3000/solus/players` | Player roster table |
| `http://localhost:3000/solus/achievements` | Achievement feed with type filter |
| `http://localhost:3000/solus/events` | "No events yet" placeholder |
| `http://localhost:3000/solus/bingo` | "No active bingo event" placeholder |
| `http://localhost:3000/nonexistent` | 404 "Clan Not Found" page |
| `http://localhost:3000/solus/players/TestPlayer` | Player profile (if TestPlayer exists in DB) |

- [ ] **Step 3: Verify the build passes cleanly**

```bash
cd C:/dev/clan-platform-web
npm run build
```

Expected: no errors, all pages statically optimized or server-rendered.

- [ ] **Step 4: Verify the "Login with Discord" button renders**

On any clan page, the nav bar should show a blue "Login with Discord" button on the right side. Clicking it won't work yet (Discord app not configured), but the button should be visible.

- [ ] **Step 5: Commit any final adjustments**

If any issues were found and fixed:

```bash
cd C:/dev/clan-platform-web
git add .
git commit -m "fix: integration adjustments from final verification"
```
