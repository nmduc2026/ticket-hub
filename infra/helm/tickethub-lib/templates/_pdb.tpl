{{- define "tickethub-lib.pdb" -}}
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: {{ include "tickethub-lib.fullname" . }}
  labels:
    {{- include "tickethub-lib.labels" . | nindent 4 }}
spec:
  maxUnavailable: {{ .Values.pdb.maxUnavailable }}
  selector:
    matchLabels:
      {{- include "tickethub-lib.selectorLabels" . | nindent 6 }}
{{- end -}}
