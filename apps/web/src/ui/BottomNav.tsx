import type { NavTab } from "../domain/types";

const TABS: { id: NavTab; label: string; icon: string }[] = [
  { id: "markets", label: "Markets", icon: "📈" },
  { id: "portfolio", label: "Portfolio", icon: "◎" },
  { id: "engine", label: "Engine", icon: "⚡" },
  { id: "wallet", label: "Wallet", icon: "◈" },
];

type Props = {
  active: NavTab;
  onSelect: (t: NavTab) => void;
};

export function BottomNav({ active, onSelect }: Props) {
  return (
    <nav className="pb-bottom-nav">
      <div className="pb-nav-inner">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            className="pb-nav-item"
            data-active={active === t.id}
            onClick={() => onSelect(t.id)}
          >
            <span aria-hidden>{t.icon}</span>
            {t.label}
          </button>
        ))}
      </div>
    </nav>
  );
}
