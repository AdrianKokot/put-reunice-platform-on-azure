import {
  ChangeDetectionStrategy,
  Component,
  inject,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { resourceIdFromRoute } from '@eunice/modules/shared/util';
import {
  filter,
  Observable,
  of,
  startWith,
  Subject,
  switchMap,
  takeUntil,
} from 'rxjs';
import {
  Resource,
  ResourceService,
  ResourceType,
} from '@eunice/modules/shared/data-access';
import { TuiDestroyService, TuiLetModule } from '@taiga-ui/cdk';
import {
  TuiPreviewDialogService,
  TuiPreviewModule,
} from '@taiga-ui/addon-preview';
import {
  TuiButtonModule,
  TuiDialogContext,
  TuiHintModule,
  TuiLoaderModule,
  TuiScrollbarModule,
  TuiSvgModule,
} from '@taiga-ui/core';
import { PolymorpheusContent } from '@tinkoff/ng-polymorpheus';
import { TranslateModule } from '@ngx-translate/core';
import { TuiFilesModule, TuiMarkerIconModule } from '@taiga-ui/kit';
import { FileIconPipeModule } from '@eunice/modules/shared/ui';

type FilePreviewUIState = {
  pagination: {
    index: number;
    size: number;
  };
  title: string;
  fileId: Resource['id'];
} & (
  | { loading: true }
  | { loading: false; content: PolymorpheusContent; type: 'url' }
  | { loading: false; type: 'unsupported' }
);

@Component({
  selector: 'eunice-page-resources-list',
  standalone: true,
  imports: [
    CommonModule,
    TuiLetModule,
    TranslateModule,
    TuiFilesModule,
    TuiButtonModule,
    TuiMarkerIconModule,
    TuiPreviewModule,
    TuiLoaderModule,
    TuiSvgModule,
    FileIconPipeModule,
    TuiScrollbarModule,
    TuiHintModule,
  ],
  templateUrl: './page-resources-list.component.html',
  styleUrls: ['./page-resources-list.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [TuiDestroyService],
})
export class PageResourcesListComponent {
  @ViewChild('preview')
  private readonly _previewTemplate?: TemplateRef<TuiDialogContext>;

  private readonly _destroyed$ = inject(TuiDestroyService);
  private readonly _fileService = inject(ResourceService);
  private readonly _previewService = inject(TuiPreviewDialogService);

  readonly files$ = resourceIdFromRoute('pageId').pipe(
    switchMap((id) => this._fileService.getByPage(id).pipe(startWith(null))),
  );

  readonly _previewState$ = new Subject<Parameters<typeof this.showFile>>();
  readonly previewUIState$ = this._previewState$.pipe(
    filter(
      (data): data is [Required<(typeof data)[0]>, boolean] => data[0] !== null,
    ),
    switchMap(([data, shouldOpenPreview]): Observable<FilePreviewUIState> => {
      const initialState: FilePreviewUIState = {
        pagination: {
          index: data.index,
          size: data.length,
        },
        fileId: data.file.id,
        title: data.file.name,
        loading: true,
      };

      if (shouldOpenPreview)
        this._previewService
          .open(this._previewTemplate ?? '')
          .pipe(takeUntil(this._destroyed$))
          .subscribe();

      if (data.file.type?.startsWith('image'))
        return of({
          ...initialState,
          loading: false,
          type: 'url',
          content: `${this._fileService.apiUrl}/api/resources/${data.file.id}/download`,
        });

      return of({ ...initialState, loading: false, type: 'unsupported' });
    }),
  );

  showFile(
    data: {
      file?: Resource;
      index: number;
      length: number;
    },
    open = false,
  ): void {
    this._previewState$.next([data, open]);
  }

  protected readonly ResourceType = ResourceType;
  readonly ApiUrl = this._fileService.apiUrl;
}
