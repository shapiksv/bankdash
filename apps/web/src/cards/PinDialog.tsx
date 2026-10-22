import { useState, type FormEvent } from 'react'
import type { Card } from '../api/cards'
import { FormAlert, PrimaryButton, TextField } from '../components/form'
import { pinError } from './pinRules'
import { useSetCardPin } from './queries'
import { CancelButton, Modal } from './Modal'

interface PinDialogProps {
  card: Card
  onDone: (message: string) => void
  onCancel: () => void
}

/**
 * Sets the first PIN of an INACTIVE card (which activates it) or changes the PIN of an ACTIVE one. Weak PINs are
 * flagged before sending; the backend's 422 (e.g. the current PIN again) is shown as it comes.
 */
export function PinDialog({ card, onDone, onCancel }: PinDialogProps) {
  const first = card.status === 'INACTIVE'
  const setPin = useSetCardPin()
  const [pin, setPinValue] = useState('')
  const [error, setError] = useState<string>()

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const weak = pinError(pin)
    setError(weak)
    if (weak) return
    setPin.mutate(
      { cardId: card.id, pin },
      { onSuccess: () => onDone(first ? 'PIN set. The card is now active.' : 'PIN changed') },
    )
  }

  return (
    <Modal title={first ? 'Set PIN' : 'Change PIN'} onClose={onCancel}>
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        {setPin.isError && <FormAlert>{setPin.error.message}</FormAlert>}
        <TextField
          label="New PIN"
          type="password"
          inputMode="numeric"
          autoComplete="off"
          maxLength={4}
          autoFocus
          value={pin}
          onChange={(event) => {
            setPinValue(event.target.value)
            setError(undefined)
            setPin.reset()
          }}
          error={error}
          hint="4 digits, not easy to guess"
        />
        <div className="flex justify-end gap-3">
          <CancelButton onClick={onCancel} disabled={setPin.isPending} />
          <PrimaryButton type="submit" disabled={setPin.isPending}>
            {setPin.isPending ? 'Saving…' : 'Save PIN'}
          </PrimaryButton>
        </div>
      </form>
    </Modal>
  )
}
