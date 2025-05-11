import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient, HttpParams } from '@angular/common/http';
import { PageSearchHitDto } from '../models/page';
import { environment } from '@eunice/environment';

@Injectable({
  providedIn: 'root',
})
export class SearchService {
  private readonly _resourceUrl = `${environment.apiUrl}/api/search`;
  private readonly _http = inject(HttpClient);

  searchPages(query: string): Observable<PageSearchHitDto[]> {
    return this._http.get<PageSearchHitDto[]>(`${this._resourceUrl}/pages`, {
      params: new HttpParams({ fromObject: { query } }),
    });
  }
}
