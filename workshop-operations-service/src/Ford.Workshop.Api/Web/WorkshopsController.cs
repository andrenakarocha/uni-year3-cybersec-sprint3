using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Ford.Workshop.Api.Web;

[ApiController]
[Route("api/v1/workshops")]
public sealed class WorkshopsController : ControllerBase
{
    [HttpGet]
    [AllowAnonymous]
    public IActionResult List() => Ok(new[]
    {
        new { id = "SP-CENTER-01", name = "Ford Center São Paulo", city = "São Paulo", capabilities = new[] { "EV", "ADAS", "GENERAL" } },
        new { id = "SP-ZONA-SUL-02", name = "Ford Zona Sul", city = "São Paulo", capabilities = new[] { "GENERAL", "BODYSHOP" } }
    });
}

