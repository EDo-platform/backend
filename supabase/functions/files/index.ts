import "jsr:@supabase/functions-js/edge-runtime.d.ts";

Deno.serve(async (req) => {
  try {
    const form = await req.formData();
    const file = form.get("file") as File;
    const arrayBuffer = await file.arrayBuffer();
    const filePath = `uploads/${Date.now()}_${file.name}`;

    // Storage 업로드
    const uploadRes = await fetch(
        `${Deno.env.get("SUPABASE_URL")}/storage/v1/object/${filePath}`,
        {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")}`,
            "apikey": Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
            "Content-Type": file.type
          },
          body: arrayBuffer
        }
    );

    if (!uploadRes.ok) {
      return new Response("Upload failed", { status: 500 });
    }

    // DB insert
    const insertRes = await fetch(
        `${Deno.env.get("SUPABASE_URL")}/rest/v1/files`,
        {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")}`,
            "apikey": Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
            "Content-Type": "application/json",
            "Prefer": "return=representation"
          },
          body: JSON.stringify({
            original_name: file.name,
            content_type: file.type,
            size: file.size,
            storage_path: filePath
          })
        }
    );

    const row = await insertRes.json();
    return new Response(JSON.stringify({ ok: true, file: row[0] }), {
      headers: { "Content-Type": "application/json" }
    });

  } catch (e) {
    return new Response(`Error: ${e}`, { status: 500 });
  }
});