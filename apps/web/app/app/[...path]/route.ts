import { dispatchBff } from "@/lib/bff/dispatch";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

async function handle(request: Request): Promise<Response> {
  return dispatchBff(request);
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
