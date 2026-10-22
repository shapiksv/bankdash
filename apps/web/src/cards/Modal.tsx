import { useId, type ReactNode } from 'react'

interface ModalProps {
  title: string
  role?: 'dialog' | 'alertdialog'
  onClose: () => void
  children: ReactNode
}

/** A centered modal box over a dimmed page; Escape closes it. */
export function Modal({ title, role = 'dialog', onClose, children }: ModalProps) {
  const titleId = useId()
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-title/40 p-4">
      <div
        role={role}
        aria-modal="true"
        aria-labelledby={titleId}
        onKeyDown={(event) => {
          if (event.key === 'Escape') onClose()
        }}
        className="flex w-full max-w-md flex-col gap-4 rounded-[25px] bg-surface p-6"
      >
        <h2 id={titleId} className="text-lg font-semibold text-title">
          {title}
        </h2>
        {children}
      </div>
    </div>
  )
}

/** The secondary button of a modal's actions. */
export function CancelButton({ onClick, disabled }: { onClick: () => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="h-12 rounded-full px-6 text-base font-medium text-title transition hover:bg-field"
    >
      Cancel
    </button>
  )
}
