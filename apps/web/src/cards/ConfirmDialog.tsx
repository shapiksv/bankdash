import { useEffect, useRef, type ReactNode } from 'react'
import { Modal } from './Modal'

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
  const cancelRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    cancelRef.current?.focus()
  }, [])

  return (
    <Modal title={title} role="alertdialog" onClose={onCancel}>
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
    </Modal>
  )
}
