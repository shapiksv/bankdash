import { useSearchParams } from 'react-router'
import { EditProfileTab } from './settings/EditProfileTab'
import { PreferencesTab } from './settings/PreferencesTab'
import { SecurityTab } from './settings/SecurityTab'

const TABS = [
  { id: 'profile', label: 'Edit Profile', Panel: EditProfileTab },
  { id: 'preferences', label: 'Preferences', Panel: PreferencesTab },
  { id: 'security', label: 'Security', Panel: SecurityTab },
] as const

/** The three tabs of the Setting mockup. The open tab is in the URL (?tab=), so it survives a reload. */
export function SettingsPage() {
  const [params, setParams] = useSearchParams()
  const active = TABS.find((tab) => tab.id === params.get('tab')) ?? TABS[0]

  return (
    <section aria-label="Settings" className="rounded-[25px] bg-surface p-6 lg:p-8">
      <div role="tablist" aria-label="Settings" className="mb-8 flex gap-8 border-b border-border">
        {TABS.map((tab) => {
          const selected = tab.id === active.id
          return (
            <button
              key={tab.id}
              type="button"
              role="tab"
              id={`tab-${tab.id}`}
              aria-selected={selected}
              aria-controls={`panel-${tab.id}`}
              onClick={() => setParams(tab.id === 'profile' ? {} : { tab: tab.id })}
              className={`-mb-px border-b-[3px] px-1 pb-3 text-base font-medium transition ${
                selected ? 'border-primary text-primary' : 'border-transparent text-muted hover:text-title'
              }`}
            >
              {tab.label}
            </button>
          )
        })}
      </div>
      <div role="tabpanel" id={`panel-${active.id}`} aria-labelledby={`tab-${active.id}`}>
        <active.Panel />
      </div>
    </section>
  )
}
