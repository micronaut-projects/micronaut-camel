# tag::filter[]
from micronaut.http import HttpMethod, HttpRequest, HttpResponse
from micronaut.http.annotation import RequestFilter, ServerFilter


@ServerFilter("/books/**")  # <1>
class ApiKeyFilter:

    @RequestFilter
    def check_api_key(self, request: HttpRequest) -> HttpResponse | None:
        if request.getMethod() == HttpMethod.POST and request.getHeaders().get("X-API-Key") != "s3cr3t":
            return HttpResponse.unauthorized()  # <2>
        return None  # <3>
# end::filter[]
