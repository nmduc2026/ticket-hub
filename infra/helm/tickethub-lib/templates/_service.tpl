{{- define "tickethub-lib.service" -}}
apiVersion: v1
kind: Service
metadata:
  name: {{ include "tickethub-lib.fullname" . }}
  labels:
    {{- include "tickethub-lib.labels" . | nindent 4 }}
spec:
  type: ClusterIP
  selector:
    {{- include "tickethub-lib.selectorLabels" . | nindent 4 }}
  ports:
    - name: http
      port: {{ .Values.service.port }}
      targetPort: http
{{- end -}}
