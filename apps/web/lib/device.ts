import { DEVICE_COOKIE } from "@/lib/session";

export function readDeviceId(): string {
  if (typeof document === "undefined") {
    return "web-unknown";
  }
  const match = document.cookie.split("; ").find((part) => part.startsWith(`${DEVICE_COOKIE}=`));
  if (match) {
    return decodeURIComponent(match.slice(DEVICE_COOKIE.length + 1));
  }
  return "web-pending";
}
