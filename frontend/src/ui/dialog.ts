import { reactive } from 'vue'

export const dialogState = reactive({
  open: false,
  title: '',
  message: '',
  okText: '确定',
  cancelText: '取消',
  danger: false,
  resolve: null as null | ((v: boolean) => void)
})

/** 确认弹窗：返回 Promise<boolean>，替代 ElMessageBox.confirm */
export function confirmDialog(opts: {
  title: string
  message: string
  okText?: string
  cancelText?: string
  danger?: boolean
}): Promise<boolean> {
  dialogState.title = opts.title
  dialogState.message = opts.message
  dialogState.okText = opts.okText || '确定'
  dialogState.cancelText = opts.cancelText || '取消'
  dialogState.danger = !!opts.danger
  dialogState.open = true
  return new Promise<boolean>((resolve) => {
    dialogState.resolve = resolve
  })
}

export function closeDialog(result: boolean) {
  dialogState.open = false
  const r = dialogState.resolve
  dialogState.resolve = null
  if (r) r(result)
}
