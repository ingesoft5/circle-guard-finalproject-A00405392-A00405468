{{/* Etiquetas comunes de todos los recursos. */}}
{{- define "cg.labels" -}}
app.kubernetes.io/part-of: circleguard
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- end }}

{{/* Imagen completa: <registro>/<repositorio>:<version>. Sin registro, solo <repositorio>:<version>. */}}
{{- define "cg.image" -}}
{{- $tag := required "global.imageTag es obligatorio (la version X.Y.Z publicada por el pipeline)" .root.Values.global.imageTag -}}
{{- if .root.Values.global.imageRegistry -}}
{{- printf "%s/%s:%s" .root.Values.global.imageRegistry .image $tag -}}
{{- else -}}
{{- printf "%s:%s" .image $tag -}}
{{- end -}}
{{- end }}

{{/* Huella de lo que alimenta al ConfigMap circleguard-config: si cambia, los pods se reinician. */}}
{{- define "cg.configChecksum" -}}
{{- toYaml (list .Values.config .Values.global.environment) | sha256sum -}}
{{- end }}

{{/* Nombre del Secret que leen los pods: el existente (Sealed/External Secrets) o el que crea el chart. */}}
{{- define "cg.secretName" -}}
{{- default "circleguard-secrets" .Values.existingSecret -}}
{{- end }}
