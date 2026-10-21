{{/*
Deployment chuẩn: probe, resources, securityContext non-root, root filesystem chỉ đọc (+ emptyDir cho /tmp).
Secret chỉ tham chiếu qua secretKeyRef, không nằm trong values (skill infra mục 7).
*/}}
{{- define "tickethub-lib.deployment" -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "tickethub-lib.fullname" . }}
  labels:
    {{- include "tickethub-lib.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      {{- include "tickethub-lib.selectorLabels" . | nindent 6 }}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  template:
    metadata:
      labels:
        {{- include "tickethub-lib.selectorLabels" . | nindent 8 }}
        {{- with .Values.podLabels }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
    spec:
      automountServiceAccountToken: false
      securityContext:
        runAsNonRoot: true
        runAsUser: {{ .Values.runAsUser }}
        runAsGroup: {{ .Values.runAsUser }}
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: {{ include "tickethub-lib.name" . }}
          image: {{ include "tickethub-lib.image" . | quote }}
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ .Values.containerPort }}
          {{- if or .Values.env .Values.secretEnv }}
          env:
            {{- range $name, $value := .Values.env }}
            - name: {{ $name }}
              value: {{ $value | quote }}
            {{- end }}
            {{- range .Values.secretEnv }}
            - name: {{ .name }}
              valueFrom:
                secretKeyRef:
                  name: {{ .secret }}
                  key: {{ .key }}
                  {{- if .optional }}
                  optional: true
                  {{- end }}
            {{- end }}
          {{- end }}
          startupProbe:
            httpGet:
              path: {{ .Values.probes.startup }}
              port: http
            periodSeconds: 5
            failureThreshold: {{ .Values.probes.startupFailureThreshold }}
          readinessProbe:
            httpGet:
              path: {{ .Values.probes.readiness }}
              port: http
            periodSeconds: 10
            timeoutSeconds: 3
          livenessProbe:
            httpGet:
              path: {{ .Values.probes.liveness }}
              port: http
            periodSeconds: 20
            timeoutSeconds: 3
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: [ALL]
          volumeMounts:
            - name: tmp
              mountPath: /tmp
      volumes:
        - name: tmp
          emptyDir: {}
{{- end -}}
