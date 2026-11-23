#!/usr/bin/env bash
# FinOps de CircleGuard: solo lee (no cambia nada en AWS).
#   ./scripts/finops.sh            informe: encendido ahora, consumo del mes, presupuesto y spot
#   ./scripts/finops.sh --alerta   sale con 1 si hay instancias encendidas (para correrlo en un cron o al cerrar una sesión)
# Requiere el AWS CLI con credenciales (las mismas de infra/bootstrap).
set -euo pipefail
R=${AWS_REGION:-us-east-1}
BUDGET=${BUDGET_NAME:-circleguard-mensual}
# tarifas on-demand de us-east-1 (Linux), verificadas contra Cost Explorer en docs/operaciones/costos.md
declare -A OD=([t3.large]=0.0832 [t3.xlarge]=0.1664 [t3.micro]=0.0104)

run=$(aws ec2 describe-instances --region "$R" --filters Name=instance-state-name,Values=running,pending \
  --query 'Reservations[].Instances[].[InstanceId,InstanceType,join(`,`,Tags[?Key==`Name`].Value)]' --output text)

echo "== Encendido ahora"
if [ -z "$run" ]; then echo "ninguna instancia (consumo de cómputo: USD 0/h)"; else
  tot=0
  while read -r id type name; do
    p=${OD[$type]:-0}; tot=$(awk -v a="$tot" -v b="$p" 'BEGIN{print a+b}')
    printf '%s  %-10s %-22s USD %s/h\n' "$id" "$type" "$name" "$p"
  done <<<"$run"
  echo "total USD ${tot}/h"
fi
[ "${1:-}" = "--alerta" ] && [ -n "$run" ] && { echo "ALERTA: hay instancias encendidas"; exit 1; }

echo; echo "== Consumo del mes (bruto, sin créditos), por servicio"
ini=$(date -u +%Y-%m-01); fin=$(date -u -d tomorrow +%Y-%m-%d)
aws ce get-cost-and-usage --region us-east-1 --time-period Start="$ini",End="$fin" --granularity MONTHLY \
  --metrics UnblendedCost --group-by Type=DIMENSION,Key=SERVICE \
  --filter '{"Not":{"Dimensions":{"Key":"RECORD_TYPE","Values":["Credit"]}}}' \
  --query 'ResultsByTime[0].Groups[].[Keys[0],Metrics.UnblendedCost.Amount]' --output text \
  | awk '{printf "%-38s USD %.2f\n",substr($0,1,length($0)-length($NF)-1),$NF; t+=$NF} END{printf "%-38s USD %.2f\n","TOTAL",t}'

echo; echo "== Presupuesto $BUDGET"
acct=$(aws sts get-caller-identity --query Account --output text)
aws budgets describe-budget --account-id "$acct" --budget-name "$BUDGET" \
  --query 'Budget.[BudgetLimit.Amount,CalculatedSpend.ActualSpend.Amount,CalculatedSpend.ForecastedSpend.Amount]' --output text \
  | awk '{printf "límite USD %s · gastado USD %.2f (%.0f %%) · pronóstico USD %.2f\n",$1,$2,100*$2/$1,$3}'

echo; echo "== Spot frente a on-demand (promedio de los últimos 7 días, la AZ más barata)"
for t in t3.large t3.xlarge; do
  aws ec2 describe-spot-price-history --region "$R" --instance-types "$t" --product-descriptions "Linux/UNIX" \
    --start-time "$(date -u -d '7 days ago' +%Y-%m-%dT%H:%M:%SZ)" --query 'SpotPriceHistory[].[AvailabilityZone,SpotPrice]' --output text \
  | awk -v t="$t" -v od="${OD[$t]}" '{s[$1]+=$2; n[$1]++} END{m=1e9; for(z in s){v=s[z]/n[z]; if(v<m){m=v; zz=z}} printf "%-10s spot USD %.4f/h en %s · on-demand USD %.4f/h · ahorro %.0f %%\n",t,m,zz,od,100*(1-m/od)}'
done
