import { useEffect } from 'react'

declare module 'react' {
  namespace JSX {
    interface IntrinsicElements {
      'md-filled-button': React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
        disabled?: boolean
        type?: 'button' | 'submit' | 'reset'
      }
      'md-outlined-button': React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
        disabled?: boolean
        type?: 'button' | 'submit' | 'reset'
      }
      'md-filled-tonal-button': React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
        disabled?: boolean
        type?: 'button' | 'submit' | 'reset'
      }
      'md-text-button': React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
        disabled?: boolean
        type?: 'button' | 'submit' | 'reset'
      }
      'md-outlined-text-field': React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
        label?: string
        value?: string
        type?: string
        placeholder?: string
        disabled?: boolean
        required?: boolean
        maxLength?: number
        onInput?: (e: React.FormEvent<HTMLElement>) => void
      }
      'md-outlined-select': React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
        label?: string
        value?: string
        disabled?: boolean
        onChange?: (e: React.FormEvent<HTMLElement>) => void
      }
      'md-select-option': React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
        value?: string
        selected?: boolean
      }
      'md-chip-set': React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement>
      'md-filter-chip': React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
        label?: string
        selected?: boolean
      }
      'md-circular-progress': React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
        indeterminate?: boolean
      }
      'md-divider': React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement>
    }
  }
}

let registered = false

export function useMaterialWebComponents() {
  useEffect(() => {
    if (registered || typeof window === 'undefined') return
    registered = true
    void import('@material/web/button/filled-button.js')
    void import('@material/web/button/outlined-button.js')
    void import('@material/web/button/filled-tonal-button.js')
    void import('@material/web/button/text-button.js')
    void import('@material/web/textfield/outlined-text-field.js')
    void import('@material/web/select/outlined-select.js')
    void import('@material/web/select/select-option.js')
    void import('@material/web/chips/chip-set.js')
    void import('@material/web/chips/filter-chip.js')
    void import('@material/web/progress/circular-progress.js')
    void import('@material/web/divider/divider.js')
  }, [])
}
