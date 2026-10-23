{{- define "tickethub-lib.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Tên Service cố định theo chart (app, gateway, frontend) để các thành phần gọi nhau bằng tên ngắn. */}}
{{- define "tickethub-lib.fullname" -}}
{{- default .Chart.Name .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "tickethub-lib.labels" -}}
app.kubernetes.io/name: {{ include "tickethub-lib.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: tickethub
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{- define "tickethub-lib.selectorLabels" -}}
app.kubernetes.io/name: {{ include "tickethub-lib.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "tickethub-lib.image" -}}
{{- printf "%s:%s" .Values.image.repository (required "image.tag là bắt buộc (CI cập nhật tag, không dùng latest)" .Values.image.tag) -}}
{{- end -}}
