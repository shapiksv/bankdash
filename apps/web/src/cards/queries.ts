import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { addCard, blockCard, fetchCards, setCardPin, type NewCard } from '../api/cards'
import { useUserId } from '../auth/useUserId'

/**
 * The user's cards, oldest first. A new user's first card is opened asynchronously after signup, so an empty list
 * is a normal state, not an error.
 */
export function useCards(limit?: number) {
  const userId = useUserId()
  return useQuery({
    queryKey: ['cards', userId, limit ?? 'all'],
    queryFn: () => fetchCards(limit),
    enabled: !!userId,
  })
}

/** Every card query of this user (all cards and the Dashboard's first two) refetches after a change. */
function useInvalidateCards() {
  const userId = useUserId()
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ['cards', userId] })
}

export function useAddCard() {
  const invalidate = useInvalidateCards()
  return useMutation({ mutationFn: (card: NewCard) => addCard(card), onSuccess: invalidate })
}

export function useSetCardPin() {
  const invalidate = useInvalidateCards()
  return useMutation({
    mutationFn: ({ cardId, pin }: { cardId: string; pin: string }) => setCardPin(cardId, pin),
    onSuccess: invalidate,
  })
}

export function useBlockCard() {
  const invalidate = useInvalidateCards()
  return useMutation({ mutationFn: (cardId: string) => blockCard(cardId), onSuccess: invalidate })
}
