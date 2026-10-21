import { useEffect, useId, useRef, type ReactNode } from 'react'

interface ConfirmDialogProps {
  title: string
  children: ReactNode
  confirmLabel: string
  onConfirm: () => void
  onCancel: () => void
  busy?: boolean
}

/**
 * A modal question for destructive actions. Cancel has the focus, so pressing Enter by accident does nothing
 * harmful; Escape cancels too.
 */
export function ConfirmDialog({ title, children, confirmLabel, onConfirm, onCancel, busy = false }: ConfirmDialogProps) {
  const titleId = useId()
  const cancelRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    cancelRef.current?.focus()
  }, [])

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-title/40 p-4">
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onKeyDown={(event) => {
          if (event.key === 'Escape') onCancel()
        }}
        className="flex w-full max-w-md flex-col gap-4 rounded-[25px] bg-surface p-6"
      >
        <h2 id={titleId} className="text-lg font-semibold text-title">
          {title}
        </h2>
        <div className="text-base text-muted">{children}</div>
        <div className="flex justify-end gap-3">
          <button
            ref={cancelRef}
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="h-12 rounded-full px-6 text-base font-medium text-title transition hover:bg-field"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className="h-12 rounded-full bg-danger px-6 text-base font-medium text-white transition hover:bg-danger/90 disabled:opacity-60"
          >
            {busy ? 'Please wait…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
