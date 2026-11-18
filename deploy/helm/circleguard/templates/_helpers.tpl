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

{{/* Identidad de los pods: ServiceAccount sin permisos y sin token (ninguno habla con la API de Kubernetes). */}}
{{- define "cg.podIdentity" -}}
serviceAccountName: circleguard-workload
automountServiceAccountToken: false
{{- end }}

{{/* Contexto de seguridad del pod de un servicio: sin root, con el usuario (numerico) de su imagen y seccomp por defecto. */}}
{{- define "cg.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: {{ .runAsUser }}
runAsGroup: {{ .runAsGroup }}
seccompProfile:
  type: RuntimeDefault
{{- end }}

{{/* Contexto de cada contenedor (tambien los initContainers): sin escalar privilegios, sin capacidades y con el disco de solo lectura. */}}
{{- define "cg.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end }}

{{/* El Ingress controller como origen permitido en una NetworkPolicy (k3s: Traefik en kube-system). */}}
{{- define "cg.ingressControllerPeer" -}}
- namespaceSelector:
    matchLabels:
      kubernetes.io/metadata.name: {{ .namespace }}
  podSelector:
    matchLabels:
      {{- toYaml .podLabels | nindent 6 }}
{{- end }}
