import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  fetchAvatar,
  fetchPreferences,
  fetchProfile,
  updatePreferences,
  updateProfile,
  uploadAvatar,
  type Preferences,
  type Profile,
  type ProfileInput,
} from '../api/profile'
import { useUserId } from '../auth/useUserId'

export function useProfile() {
  const userId = useUserId()
  return useQuery({ queryKey: ['profile', userId], queryFn: fetchProfile, enabled: !!userId })
}

export function usePreferences() {
  const userId = useUserId()
  return useQuery({ queryKey: ['preferences', userId], queryFn: fetchPreferences, enabled: !!userId })
}

export function useUpdateProfile() {
  const userId = useUserId()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ProfileInput) => updateProfile(input),
    onSuccess: (profile) => queryClient.setQueryData<Profile>(['profile', userId], profile),
  })
}

export function useUploadAvatar() {
  const userId = useUserId()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => uploadAvatar(file),
    // The new avatarUpdatedAt changes the avatar query's key, so the header refetches the image too.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['profile', userId] }),
  })
}

export function useUpdatePreferences() {
  const userId = useUserId()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (preferences: Preferences) => updatePreferences(preferences),
    onSuccess: (saved) => queryClient.setQueryData<Preferences>(['preferences', userId], saved),
  })
}

/**
 * The avatar as a data URL for an img src, or undefined while there is none. The image needs the Bearer token,
 * so it is fetched as a blob instead of being loaded by the img tag. A data URL (at most about 1.4 MB) needs no
 * cleanup, unlike an object URL.
 */
export function useAvatarUrl(profile: Profile | undefined): string | undefined {
  const userId = useUserId()
  const version = profile?.avatarUpdatedAt
  const { data } = useQuery({
    queryKey: ['avatar', userId, version],
    queryFn: async () => toDataUrl(await fetchAvatar()),
    enabled: !!userId && !!version,
    staleTime: Infinity,
  })
  return version ? data : undefined
}

function toDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(blob)
  })
}

/** "Charlene Reed" → "CR"; falls back to the first letter of the email. */
export function initials(fullName: string | undefined, email: string): string {
  const words = (fullName ?? '').trim().split(/\s+/).filter(Boolean)
  const letters = words.length > 1 ? words[0][0] + words[words.length - 1][0] : (words[0]?.[0] ?? email.charAt(0))
  return letters.toUpperCase() || '?'
}
